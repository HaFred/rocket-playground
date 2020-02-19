import mill._
import mill.modules.Util
import scalalib._
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import mill.contrib.buildinfo.BuildInfo
import $file.chisel3.build
import $file.firrtl.build

object myfirrtl extends firrtl.build.firrtlCrossModule("2.12.10") {
  override def millSourcePath = super.millSourcePath / 'firrtl
}

object mychisel3 extends chisel3.build.chisel3CrossModule("2.12.10") {
  override def millSourcePath = super.millSourcePath / 'chisel3
  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}

trait CommonModule extends ScalaModule {
  def scalaVersion = "2.12.10"

  override def scalacOptions = Seq("-Xsource:2.11")

  override def moduleDeps: Seq[ScalaModule] = Seq(mychisel3)

  private val macroParadise = ivy"org.scalamacros:::paradise:2.1.0"

  override def compileIvyDeps = Agg(macroParadise)

  override def scalacPluginIvyDeps = Agg(macroParadise)
}

object chiseltest extends CommonModule with SbtModule {
  override def moduleDeps: Seq[ScalaModule] = super.moduleDeps ++ Seq(treadle)
  
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::utest:latest.integration",
    ivy"com.lihaoyi::os-lib:latest.integration",
    ivy"org.scalatest::scalatest:latest.integration"
  )

  object test extends Tests {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.8",
      ivy"org.scalacheck::scalacheck:1.14.3",
    )

    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}

object treadle extends CommonModule with SbtModule {
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"org.scala-lang.modules:scala-jline:2.12.1",
    ivy"org.json4s::json4s-native:3.6.7"
  )
}

object config extends CommonModule {
  override def millSourcePath = super.millSourcePath / 'design / 'craft
}

object rocketchip extends CommonModule with SbtModule {
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}",
    ivy"org.json4s::json4s-jackson:latest.integration"
  )

  object hardfloat extends CommonModule with SbtModule

  object macros extends CommonModule with SbtModule

  override def moduleDeps = super.moduleDeps ++ Seq(config, macros, hardfloat)

  override def mainClass = Some("rocketchip.Generator")
}

object inclusivecache extends CommonModule {
  override def millSourcePath = super.millSourcePath / 'design / 'craft / 'inclusivecache

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}

object blocks extends CommonModule with SbtModule {
  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}

object shells extends CommonModule with SbtModule {
  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip, blocks)
}

object freedom extends CommonModule with SbtModule {
  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip, blocks, nvdla, shells)
}

object nvdla extends CommonModule with SbtModule {
  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}

object skeleton extends CommonModule {
  override def millSourcePath = testsocket.basePath

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip, blocks, shells)
}

object testsocket extends CommonModule {
  outer =>
  def basePath = super.millSourcePath

  override def millSourcePath = super.millSourcePath / 'design / 'craft / "fpga-test-socket"

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip, blocks, shells, skeleton)
}

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(chiseltest, rocketchip, inclusivecache, blocks, rocketchip.macros, shells)

  override def ivyDeps = Agg(
    ivy"com.lihaoyi::upickle:latest.integration",
    ivy"com.lihaoyi::pprint:latest.integration",
    ivy"org.scala-lang.modules::scala-xml:latest.integration"
  )

  object tests extends Tests {
    override def ivyDeps = Agg(ivy"com.lihaoyi::utest:latest.integration")

    def testFrameworks = Seq("utest.runner.Framework")
  }

}

