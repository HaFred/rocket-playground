package arty100t

import freechips.rocketchip._
import amba.axi4.AXI4Bundle
import diplomacy._
import devices.debug.SystemJTAGIO
import sifive.blocks.devices._
import uart._
import spi._
import playground._
import sifive.fpgashells.ip.xilinx._
import chisel3._
import chisel3.util._
import chisel3.experimental.{ExtModule, Analog, attach}

class BUFGCE extends ExtModule {
  val O = IO(Output(Bool()))
  val CE = IO(Input(Bool()))
  val I = IO(Input(Bool()))
}

class BSCANE2 extends ExtModule(Map("JTAG_CHAIN" -> 4)) {
  val TDO = IO(Input(Bool()))
  val CAPTURE = IO(Output(Bool()))
  val DRCK = IO(Output(Bool()))
  val RESET = IO(Output(Bool()))
  val RUNTEST = IO(Output(Bool()))
  val SEL = IO(Output(Bool()))
  val SHIFT = IO(Output(Bool()))
  val TCK = IO(Output(Bool()))
  val TDI = IO(Output(Bool()))
  val TMS = IO(Output(Bool()))
  val UPDATE = IO(Output(Bool()))
}

class BscanJTAG extends MultiIOModule {
  val tck: Clock = IO(Output(Clock()))
  val tms: Bool = IO(Output(Bool()))
  val tdi: Bool = IO(Output(Bool()))
  val tdo: Bool = IO(Input(Bool()))
  val tdoEnable: Bool = IO(Input(Bool()))

  val bscane2: BSCANE2 = Module(new BSCANE2)
  tdi := bscane2.TDI
  bscane2.TDO := Mux(tdoEnable, tdo, true.B)
  val bufgce = Module(new BUFGCE)
  bufgce.I := bscane2.TCK
  bufgce.CE := bscane2.SEL
  tck := bufgce.O.asClock

  val posClock: Clock = bscane2.TCK.asClock
  val negClock: Clock = (!bscane2.TCK).asClock
  /**
   * This two wire will cross two clock domain,
   * generated at [[posClock]], used in [[negClock]]
   **/
  val tdiRegisterWire = Wire(Bool())
  val shiftCounterWire = Wire(UInt(7.W))
  withReset(!bscane2.SHIFT) {
    withClock(posClock) {
      val shiftCounter = RegInit(0.U(7.W))
      val posCounter = RegInit(0.U(8.W))
      val tdiRegister = RegInit(false.B)
      posCounter := posCounter + 1.U
      when(posCounter >= 1.U && posCounter <= 7.U) {
        shiftCounter := Cat(bscane2.TDI, shiftCounter.head(6))
      }
      when(posCounter === 0.U) {
        tdiRegister := !bscane2.TDI
      }
      tdiRegisterWire := tdiRegister
      shiftCounterWire := shiftCounter
    }
    withClock(negClock) {
      val negCounter = RegInit(0.U(8.W))
      negCounter := negCounter + 1.U
      tms := MuxLookup(negCounter, false.B, Array(
        4.U -> tdiRegisterWire,
        5.U -> true.B,
        shiftCounterWire + 7.U -> true.B,
        shiftCounterWire + 8.U -> true.B)
      )
    }
  }
}


class FPGATop extends MultiIOModule {
  val top = Module(LazyModule(configToRocketModule(classOf[CustomArty100TRocketSystem], new CustomArty100TConfig)).module)

  val topInterrupts: UInt = top.interrupts
  val fpgaInterrupts = IO(Input(topInterrupts.cloneType))
  fpgaInterrupts <> topInterrupts

  val topMem: AXI4Bundle = top.outer.mem_axi4.head
  val fpgaMem = IO(topMem.cloneType)
  fpgaMem <> topMem

  val topUART: UARTPortIO = top.uart.head.asInstanceOf[UARTPortIO]
  val fpgaUART = IO(new Bundle {
    val txd = Analog(1.W)
    val rxd = Analog(1.W)
  })
  IOBUF(fpgaUART.rxd, topUART.txd)
  topUART.rxd := IOBUF(fpgaUART.txd)

  val topSPI: SPIPortIO = top.qspi.head.asInstanceOf[SPIPortIO]
  val fpgaSPI = IO(new Bundle {
    val sck = Analog(1.W)
    val cs = Analog(1.W)
    val dq = Vec(4, Analog(1.W))
  })

  IOBUF(fpgaSPI.sck, topSPI.sck)
  IOBUF(fpgaSPI.cs, topSPI.cs(0))
  fpgaSPI.dq.zipWithIndex.foreach {
    case (io: Analog, i: Int) =>
      val pad = Module(new IOBUF)
      pad.io.I := topSPI.dq(i).o
      topSPI.dq(i).i := pad.io.O
      pad.io.T := topSPI.dq(i).oe
      attach(pad.io.IO, io)
      PULLUP(io)
  }


  val topJtag: SystemJTAGIO = top.debug.head.systemjtag.head
  val fpgaJtag = Module(new BscanJTAG)
  topJtag.reset := reset
  topJtag.mfr_id := 0x489.U(11.W)
  topJtag.part_number := 0.U(16.W)
  topJtag.version := 2.U(4.W)
  topJtag.jtag.TCK := fpgaJtag.tck
  topJtag.jtag.TMS := fpgaJtag.tms
  topJtag.jtag.TDI := fpgaJtag.tdi
  fpgaJtag.tdo := topJtag.jtag.TDO.data
  fpgaJtag.tdoEnable := topJtag.jtag.TDO.driven

  /** second QSPI will become SDIO */
  val topSDIO: SPIPortIO = top.qspi.last.asInstanceOf[SPIPortIO]

  val fpgaSDIO = IO(new Bundle {
    /** pmod 3 */
    val cmd = Analog(1.W)
    /** pmod 4 */
    val sck = Analog(1.W)
    /** pmod 8 */
    val data0 = Analog(1.W)
    /** pmod 10 */
    val data3 = Analog(1.W)
  })

  val misoSync = RegInit(VecInit(Seq.fill(2)(false.B)))
  val miso = Wire(Bool())
  val mosi = Wire(Bool())
  mosi := topSDIO.dq(0).o
  misoSync(0) := miso
  misoSync(1) := misoSync(0)
  topSDIO.dq(0).i := false.B
  topSDIO.dq(1).i := false.B
  topSDIO.dq(2).i := misoSync(1)
  topSDIO.dq(3).i := false.B
  IOBUF(fpgaSDIO.sck, topSDIO.sck)
  IOBUF(fpgaSDIO.cmd, mosi)
  miso := IOBUF(fpgaSDIO.data0)
  IOBUF(fpgaSDIO.data3, topSDIO.cs(0))
}