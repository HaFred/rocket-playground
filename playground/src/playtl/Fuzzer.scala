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
class TLFuzzer(inFlight: Int = 32)(implicit p: Parameters) extends LazyModule {

  def noiseMaker(width: Int, increment: Bool): UInt = chisel3.util.random.LFSR(512, increment = increment).head(width)

  val clientParams = Seq(TLMasterParameters.v1(
    name = "Fuzzer",
    sourceId = IdRange(0, inFlight)
  ))

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(clientParams)))

  lazy val module = new LazyModuleImp(this) {

    val (out, edge) = node.out.head

    val (aFirst, aLast, aRequestDone) = edge.firstlast(out.a)
    val (dFirst, dLast, dResponseDone) = edge.firstlast(out.d)

    // Source ID generation
    val idMap = Module(new IDMapGenerator(inFlight))
    idMap.io.alloc.ready := out.a.ready
    idMap.io.free.valid := dResponseDone
    idMap.io.free.bits := out.d.bits.source

    val src = idMap.io.alloc.bits holdUnless aFirst
    val addrReg = RegInit(0.U(31.W))
    val random = chisel3.util.random.LFSR(33, aRequestDone, Some(BigInt(31, scala.util.Random).abs))
    val rw = random.head(1).asBool() === true.B
    val data = random.tail(1)
    val addr = WireInit(addrReg << 2).asUInt() & 0x1ff.U
    val size = 2.U

    val (_, gbits) = edge.Get(src, addr, size)
    val (_, pfbits) = edge.Put(src, addr, size, data)

    out.a.bits := Mux(rw, gbits, pfbits)
    out.a.valid := true.B
    out.d.ready := true.B

    when(dResponseDone) {
      addrReg := addrReg + 1.U
      printf("read from %d: %x", out.d.bits.source, out.d.bits.data)
    }
  }
}

object TLFuzzer {
  def apply(inFlight: Int = 32,
            nOrdered: Option[Int] = None)(implicit p: Parameters): TLOutwardNode = {
    val fuzzer = LazyModule(new TLFuzzer(inFlight))
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
  ram.node := TLFragmenter(4, 256) := TLWidthWidget(16) := xbar.node

  lazy val module = new LazyModuleImp(this)
}

object testTLFuzzer extends App {
  implicit val p = Parameters((site, here, up) => {
    case MonitorsEnabled => false
  })
  RawTester.test(LazyModule(new TLFuzzRAM()).module, Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
    c =>
      c.clock.setTimeout(0)
      c.clock.step(300)
  }
}
