package unittester

import chiseltest.{RawTester, _}
import chiseltest.internal.WriteVcdAnnotation
import diplomatictester.Utils._
import diplomatictester._
import fpga.{CustomArty100TConfig, CustomArty100TRocketSystem, CustomArty100TRocketSystemModuleImp}
import freechips.rocketchip._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import logger.{LogLevel, LogLevelAnnotation}
import playground._
import sifive.blocks.inclusivecache.InclusiveCache

class CustomArty100TRocketSystemDut(implicit p: Parameters) extends CustomArty100TRocketSystem { lm =>
  override lazy val module = new CustomArty100TRocketSystemModuleImp(this) {
    val l2 = lm.childrenFinder(_.name == "l2").asInstanceOf[InclusiveCache]
    dutModule(l2.module)
    val dutAuto = dutIO(l2.module.auto, "dutAuto")
  }
}

object InclusiveCacheTester extends App {
  val lm = LazyModule(configToRocketModule(classOf[CustomArty100TRocketSystemDut], new CustomArty100TConfig))
  RawTester.test(lm.module, Seq(WriteVcdAnnotation, LogLevelAnnotation(LogLevel.Info))) {
    c =>
      c.clock.step()
  }
}