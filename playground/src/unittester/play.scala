package chiseltest

import chipsalliance.rocketchip.config._
import firrtl.AnnotationSeq
import freechips.rocketchip.diplomacy._

abstract class LazyDut()(implicit p: Parameters) extends LazyModule {
  def testNodes[T <: BaseNode]: Seq[BaseNode]
  // module is only be allowed to use [[LazyModuleImp]] since tester2 not support [[RawModule]]
  def module: LazyModuleImp
}

class LMTester[L <: LazyDut](lm: L) {
  def annotationSeq: AnnotationSeq = Seq.empty

  def test(testFn: LazyModuleImp => Unit): Unit = {
    val testName = s"lazymodule_test_${System.currentTimeMillis()}"
    val tester = new RawTester(testName)
    tester.test(lm.module, annotationSeq)(testFn)
  }
}