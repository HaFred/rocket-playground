package playtl

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import chisel3.util.experimental.BoringUtils

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

class DummyFuzzer(inFlight: Int = 32)(implicit p: Parameters) extends LazyModule {

  def noiseMaker(width: Int, increment: Bool): UInt = chisel3.util.random.LFSR(512, increment = increment).head(width)

  val clientParams = Seq(TLMasterParameters.v1(
    name = "Fuzzer",
    sourceId = IdRange(0, inFlight)
  ))

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(clientParams)))

  lazy val module = new LazyModuleImp(this) {
    val (out, edge: TLEdgeOut) = node.out.head
    val outDebug = Wire(out.cloneType)
    outDebug <> out
    BoringUtils.addSource(outDebug, s"TLFuzzer")
  }
}

object DummyFuzzer {
  def apply(inFlight: Int = 32,
            nOrdered: Option[Int] = None)(implicit p: Parameters): TLOutwardNode = {
    val fuzzer = LazyModule(new DummyFuzzer(inFlight))
    fuzzer.node
  }
}

class TLFuzzRAM(implicit p: Parameters) extends LazyModule {
  val ram = LazyModule(new TLRAM(AddressSet(0x100, 0xff)))
  val ram2 = LazyModule(new TLRAM(AddressSet(0, 0xff), beatBytes = 16))
  val xbar = LazyModule(new TLXbar)
  val fuzz = LazyModule(new DummyFuzzer)
  val ramModel = LazyModule(new TLRAMModel("TLFuzzRAM"))

  xbar.node := ramModel.node := fuzz.node
  ram2.node := TLFragmenter(16, 256) := xbar.node
  ram.node := TLFragmenter(4, 256) := TLWidthWidget(16) := xbar.node

  lazy val module = new LazyModuleImp(this) {
    val debug = IO(fuzz.module.out.cloneType)
    val outDebug = Wire(Flipped(fuzz.module.out.cloneType))
    debug <> outDebug
    BoringUtils.addSink(outDebug, s"TLFuzzer")
  }
}

object testTLFuzzer extends App {
  implicit val p = Parameters((site, here, up) => {
    case MonitorsEnabled => false
  })
  val lm = LazyModule(new TLFuzzRAM())
  RawTester.test(lm.module, Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) {
    c =>
      c.clock.setTimeout(0)
      // TODO: real fuzzer.
      val opcode = 4 // Get
      val param = 0
      val size = 1
      val mask = 0xff
      val source = 0
      val address = 0x100
      val data = 0x100
      val corrupt = false

      c.clock.step(10)
      c.debug.a.bits.opcode.poke(opcode.U)
      c.debug.a.bits.param.poke(param.U)
      c.debug.a.bits.size.poke(size.U)
      c.debug.a.bits.source.poke(source.U)
      c.debug.a.bits.address.poke(address.U)
      c.debug.a.bits.mask.poke(mask.U)
      c.debug.a.bits.data.poke(data.U)
      c.debug.a.bits.corrupt.poke(corrupt.B)
      c.clock.step(1)
      c.clock.step(10)
  }
}
