package unittester

import freechips.rocketchip._
import config._
import diplomacy._
import playground._
import chisel3._
import chisel3.experimental.DataMirror
import chiseltest._
import chiseltest.RawTester
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import diplomatictester._
import diplomatictester.Utils._
import fpga.{CustomArty100TConfig, CustomArty100TRocketSystem, CustomArty100TRocketSystemModuleImp}
import freechips.rocketchip.subsystem.{BaseSubsystem, WithInclusiveCache}
import logger.{LogLevel, LogLevelAnnotation}
import os._
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