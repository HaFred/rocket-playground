package unittester
import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, NoRunFirrtlCompilerAnnotation}
import chiseltest._
import chiseltest.experimental._
import firrtl.options.TargetDirAnnotation
import firrtl.stage.FirrtlFileAnnotation
import fpga._
import freechips.rocketchip.diplomacy.{LazyModule, MixedAdapterNode, MixedNode, SinkNode}
import playground._

class StaticModule[T <: Data](ioLit: T) extends MultiIOModule {
  val out = IO(Output(chiselTypeOf(ioLit)))
  out := ioLit
}

object play extends App {
  // filter a lazymodule
  val lm = LazyModule(
    configToRocketModule(
      classOf[CustomArty100TRocketSystem],
      new CustomArty100TConfig
    )
  )
  val target = lm.getChildren.filter(_.name == "l2").head
  (new chisel3.stage.ChiselStage).run(
    Seq(
      ChiselGeneratorAnnotation(() => target.module),
      TargetDirAnnotation("circuit"),
      NoRunFirrtlCompilerAnnotation // speed up test
    )
  )
  
  // need a small patch to diplomacy
  //diff --git a/src/main/scala/diplomacy/LazyModule.scala b/src/main/scala/diplomacy/LazyModule.scala
  //index fb4b27da..d884c42c 100644
  //--- a/src/main/scala/diplomacy/LazyModule.scala
  //+++ b/src/main/scala/diplomacy/LazyModule.scala
  //@@ -118,6 +118,8 @@ abstract class LazyModule()(implicit val p: Parameters)
  //  }
  //
  //  def getChildren = children
  //
  //  {+def getNodes = nodes+}
  //}
  //
  //object LazyModule

  val nodes = target.getNodes
  nodes.foreach {
    case n: MixedAdapterNode[_, _, _, _, _, _, _, _] => pprint.pprintln(n.edges)
    case n: SinkNode[_,_,_,_,_] => pprint.pprintln(n.edges)
  }

//
//  // reconstruct interface
//  (new firrtl.stage.FirrtlStage).run(
//    Seq(
//      FirrtlFileAnnotation("circuit/InclusiveCache.fir"),
//    )
//  )

  //
//  RawTester.test(target.module) { dut =>
//    println(dut.auto.peek())
//  }
}
