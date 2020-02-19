package unittester
import chisel3._
import chiseltest._
import chiseltest.experimental._
import fpga._
import freechips.rocketchip.diplomacy.LazyModule
import playground._

class StaticModule[T <: Data](ioLit: T) extends MultiIOModule {
  val out = IO(Output(chiselTypeOf(ioLit)))
  out := ioLit
}

object play extends App {
  val lm = LazyModule(
    configToRocketModule(
      classOf[CustomArty100TRocketSystem],
      new CustomArty100TConfig
    )
  )
  val target = lm.getChildren.filter(_.name == "bh").head
  RawTester.test(target.module) { dut =>
    println(dut.auto.peek())
  }
}
