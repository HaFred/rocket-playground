package playtl

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._

class IDMapGenerator(numIds: Int) extends Module {
  require(numIds > 0)

  val w = log2Up(numIds)
  val io = IO(new Bundle {
    val free = Flipped(Decoupled(UInt(w.W)))
    val alloc = Decoupled(UInt(w.W))
  })

  io.free.ready := true.B

  // True indicates that the id is available
  val bitmap = RegInit(((BigInt(1) << numIds) - 1).U(numIds.W))

  val select = (~(leftOR(bitmap) << 1)).asUInt & bitmap
  io.alloc.bits := OHToUInt(select)
  io.alloc.valid := bitmap.orR()

  val clr = WireDefault(0.U(numIds.W))
  when(io.alloc.fire()) {
    clr := UIntToOH(io.alloc.bits)
  }

  val set = WireDefault(0.U(numIds.W))
  when(io.free.fire()) {
    set := UIntToOH(io.free.bits)
  }

  bitmap := (bitmap & (~clr).asUInt) | set
  assert(!io.free.valid || !(bitmap & (~clr).asUInt) (io.free.bits)) // No double freeing
}


/** TLFuzzer drives test traffic over TL2 links. It generates a sequence of randomized
  * requests, and issues legal ones into the DUT. TODO: Currently the fuzzer only generates
  * memory operations, not permissions transfers.
  *
  * @param inFlight is the number of operations that can be in-flight to the DUT concurrently
  */
class TLFuzzer(inFlight: Int = 32,
               nOrdered: Option[Int] = None)(implicit p: Parameters) extends LazyModule {

  def noiseMaker(width: Int, increment: Bool): UInt = chisel3.util.random.LFSR(512, increment = increment).head(width)

  val clientParams = if (nOrdered.isDefined) {
    val n = nOrdered.get
    require(n > 0, s"nOrdered must be > 0, not $n")
    require((inFlight % n) == 0, s"inFlight (${inFlight}) must be evenly divisible by nOrdered (${nOrdered}).")
    Seq.tabulate(n) { i =>
      TLMasterParameters.v1(name = s"OrderedFuzzer$i",
        sourceId = IdRange(i * (inFlight / n), (i + 1) * (inFlight / n)),
        requestFifo = true)
    }
  } else {
    Seq(TLMasterParameters.v1(
      name = "Fuzzer",
      sourceId = IdRange(0, inFlight)
    ))
  }

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(clientParams)))

  lazy val module = new LazyModuleImp(this) {

    val (out, edge) = node.out.head

    // Extract useful parameters from the TL edge
    val maxTransfer = edge.manager.maxTransfer
    val beatBytes = edge.manager.beatBytes
    val maxLgBeats = log2Up(maxTransfer / beatBytes)
    val addressBits = edge.manager.maxAddress.toInt
    val sizeBits = edge.bundle.sizeBits
    val dataBits = edge.bundle.dataBits

    // Progress within each operation
    val a = out.a.bits
    val (a_first, a_last, req_done) = edge.firstlast(out.a)

    val d = out.d.bits
    val (d_first, d_last, resp_done) = edge.firstlast(out.d)

    // Source ID generation
    val idMap = Module(new IDMapGenerator(inFlight))
    val src = idMap.io.alloc.bits holdUnless a_first
    // Increment random number generation for the following subfields
    val inc = Wire(Bool())
    val inc_beat = Wire(Bool())
    val arth_op_3 = noiseMaker(3, inc)
    val arth_op = Mux(arth_op_3 > 4.U, 4.U, arth_op_3)
    val log_op = noiseMaker(2, inc)
    val amo_size = 2.U + noiseMaker(1, inc) // word or dword
    val size = noiseMaker(sizeBits, inc)
    val addr = noiseMaker(addressBits, inc) & (~UIntToOH1(size, addressBits)).asUInt
    val mask = noiseMaker(beatBytes, inc_beat) & edge.mask(addr, size)
    val data = noiseMaker(dataBits, inc_beat)

    // Actually generate specific TL messages when it is legal to do so
    val (glegal, gbits) = edge.Get(src, addr, size)
    val (pflegal, pfbits) = if (edge.manager.anySupportPutFull) {
      edge.Put(src, addr, size, data)
    } else {
      (glegal, gbits)
    }
    val (pplegal, ppbits) = if (edge.manager.anySupportPutPartial) {
      edge.Put(src, addr, size, data, mask)
    } else {
      (glegal, gbits)
    }
    val (alegal, abits) = if (edge.manager.anySupportArithmetic) {
      edge.Arithmetic(src, addr, size, data, arth_op)
    } else {
      (glegal, gbits)
    }
    val (llegal, lbits) = if (edge.manager.anySupportLogical) {
      edge.Logical(src, addr, size, data, log_op)
    } else {
      (glegal, gbits)
    }
    val (hlegal, hbits) = if (edge.manager.anySupportHint) {
      edge.Hint(src, addr, size, 0.U)
    } else {
      (glegal, gbits)
    }

    val legal_dest = edge.manager.containsSafe(addr)

    // Pick a specific message to try to send
    val a_type_sel = noiseMaker(3, inc)

    val legal = legal_dest && MuxLookup(a_type_sel, glegal, Seq(
      "b000".U -> glegal,
      "b001".U -> pflegal,
      "b010".U -> pplegal,
      "b011".U -> alegal,
      "b100".U -> llegal,
      "b101".U -> hlegal)
    )

    val bits = MuxLookup(a_type_sel, gbits, Seq(
      "b000".U -> gbits,
      "b001".U -> pfbits,
      "b010".U -> ppbits,
      "b011".U -> abits,
      "b100".U -> lbits,
      "b101".U -> hbits)
    )

    // Wire up Fuzzer flow control
    out.a.valid := !reset.asBool && legal && (!a_first || idMap.io.alloc.valid)
    idMap.io.alloc.ready := legal && a_first && out.a.ready
    idMap.io.free.valid := d_first && out.d.fire()
    idMap.io.free.bits := out.d.bits.source

    out.a.bits := bits
    out.b.ready := true.B
    out.c.valid := false.B
    out.d.ready := true.B
    out.e.valid := false.B

    // Increment the various progress-tracking states
    inc := !legal || req_done
    inc_beat := !legal || out.a.fire()


  }
}

object TLFuzzer {
  def apply(inFlight: Int = 32,
            nOrdered: Option[Int] = None)(implicit p: Parameters): TLOutwardNode = {
    val fuzzer = LazyModule(new TLFuzzer(inFlight, nOrdered))
    fuzzer.node
  }
}

class TLFuzzRAM(implicit p: Parameters) extends LazyModule {
  val ram = LazyModule(new TLRAM(AddressSet(0x100, 0xff)))
  val ram2 = LazyModule(new TLRAM(AddressSet(0, 0xff), beatBytes = 16))
  val xbar = LazyModule(new TLXbar)
  val fuzz = LazyModule(new TLFuzzer)
  val ramModel = LazyModule(new TLRAMModel("TLFuzzRAM"))

  xbar.node := ramModel.node := fuzz.node
  ram2.node := TLFragmenter(16, 256) := xbar.node
  ram.node := TLFragmenter(4, 256) := TLWidthWidget(16)  := xbar.node

  lazy val module = new LazyModuleImp(this)
}

object testTLFuzzer extends App {
  implicit val p = Parameters((site, here, up) => {
    case MonitorsEnabled => false
  })
  RawTester.test(LazyModule(new TLFuzzRAM()).module, Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
    c =>
      c.clock.setTimeout(0)
      c.clock.step(10000)
  }
}
