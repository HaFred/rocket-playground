package unittester

import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import chiseltest.{RawTester, _}
import diplomatictester.Utils._
import diplomatictester._
import fpga.{CustomArty100TConfig, CustomArty100TRocketSystem, CustomArty100TRocketSystemModuleImp}
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink.TLEdgeIn
import logger.{LogLevel, LogLevelAnnotation}
import playground._
import sifive.blocks.inclusivecache.InclusiveCache

class CustomArty100TRocketSystemDut(implicit p: Parameters) extends CustomArty100TRocketSystem {
  lm =>
  override lazy val module = new CustomArty100TRocketSystemModuleImp(this) {
    val l2 = lm.childrenFinder(_.name == "l2").asInstanceOf[InclusiveCache]
    dutModule(l2.module)
    val dutAuto = dutIO(l2.module.auto, "dutAuto")
  }
}

object InclusiveCacheTester extends App {
  val soc = LazyModule(configToRocketModule(classOf[CustomArty100TRocketSystemDut], new CustomArty100TConfig))
  RawTester.test(soc.module, Seq(WriteVcdAnnotation, LogLevelAnnotation(LogLevel.Info), VerilatorBackendAnnotation)) {
    c =>
      implicit val clock = c.clock
      val dut = soc.module
      val edges = dut.l2.node.edges
      val edgeIn: TLEdgeIn = edges.in.head
      val edgeOut = edges.out.head
      val ctlEdgeIn = dut.l2.ctlnode.get.edges.in.head

      val in = c.dutAuto.tlBundle("in")
      val out = c.dutAuto.tlBundle("out")
      val ctlIn = c.dutAuto.tlBundle("ctl_in")

      val edgeInId = edgeIn.client.clients.head.sourceId

      val clientA: ClientA = in.clientA(edgeIn)
      val clientB: ClientB = in.clientB(edgeIn)
      val clientC: ClientC = in.clientC(edgeIn)
      val clientD: ClientD = in.clientD(edgeIn)
      val clientE: ClientE = in.clientE(edgeIn)

      val managerA: ManagerA = out.managerA(edgeOut)
      val managerB: ManagerB = out.managerB(edgeOut)
      val managerC: ManagerC = out.managerC(edgeOut)
      val managerD: ManagerD = out.managerD(edgeOut)
      val managerE: ManagerE = out.managerE(edgeOut)

      val ctlClientA: ClientA = ctlIn.clientA(ctlEdgeIn)
      val ctlClientB: ClientB = ctlIn.clientB(ctlEdgeIn)
      val ctlClientC: ClientC = ctlIn.clientC(ctlEdgeIn)
      val ctlClientD: ClientD = ctlIn.clientD(ctlEdgeIn)
      val ctlClientE: ClientE = ctlIn.clientE(ctlEdgeIn)

      /** @todo write you own test logic here. */
      ctlClientA.Get(Poke())(2, edgeInId.start, 0x2010000, 0xff)
      ctlClientD.AccessAckData(Expect())(2, edgeInId.start, false, false, 0x00)
  }
}