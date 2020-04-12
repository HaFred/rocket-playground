package unittester

import freechips.rocketchip._
import diplomacy._
import playground._
import chisel3._
import fpga.{CustomArty100TConfig, CustomArty100TRocketSystem}
import os._


object InclusiveDut extends App {
  val lm = LazyModule(configToRocketModule(classOf[CustomArty100TRocketSystem], new CustomArty100TConfig))
  (new chisel3.stage.ChiselStage).emitChirrtl(lm.module)
  write(pwd / "circuit" / "graph", lm.graphML)
}