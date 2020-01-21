import mill._
import scalalib._

trait CommonModule extends ScalaModule {
  def scalaVersion = "2.12.10"

  def scalacOptions = Seq("-Xsource:2.11")

  def ivyDeps = Agg(
    ivy"edu.berkeley.cs::chisel3:3.2.2",
  )

  private val macroParadise = ivy"org.scalamacros:::paradise:2.1.0"

  def compileIvyDeps = Agg(macroParadise)

  def scalacPluginIvyDeps = Agg(macroParadise)
}

object config extends CommonModule {
  def millSourcePath = super.millSourcePath / 'design / 'craft
}

object rocketchip extends CommonModule with SbtModule {
  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}",
    ivy"org.json4s::json4s-jackson:3.6.1"
  )

  object hardfloat extends CommonModule with SbtModule

  object macros extends CommonModule with SbtModule

  def moduleDeps = Seq(config, macros, hardfloat)

  def mainClass = Some("rocketchip.Generator")
}

object inclusivecache extends CommonModule {
  def millSourcePath = super.millSourcePath / 'design / 'craft / 'inclusivecache

  def moduleDeps = Seq(rocketchip)
}

object blocks extends CommonModule with SbtModule {
  def moduleDeps = Seq(rocketchip)
}

object shells extends CommonModule with SbtModule {
  def moduleDeps = Seq(rocketchip, blocks)
}

object freedom extends CommonModule with SbtModule {
  def moduleDeps = Seq(rocketchip, blocks, nvdla, shells)
}

object nvdla extends CommonModule with SbtModule {
  def moduleDeps = Seq(rocketchip)
}

object skeleton extends CommonModule { 
  def millSourcePath = testsocket.basePath

  def moduleDeps = Seq(rocketchip, blocks, shells)
}

object testsocket extends CommonModule { outer =>
  def basePath = super.millSourcePath

  def millSourcePath = super.millSourcePath / 'design / 'craft / "fpga-test-socket" 

  def moduleDeps = Seq(rocketchip, blocks, shells, skeleton)
}

object playground extends CommonModule {
  def moduleDeps = Seq(rocketchip, inclusivecache, blocks, rocketchip.macros, shells)
  
  def ivyDeps = Agg(
    ivy"com.lihaoyi::upickle:0.9.8",
    ivy"com.lihaoyi::pprint:0.5.8",
    ivy"org.scala-lang.modules::scala-xml:1.2.0"
  )
  object tests extends Tests {
    def ivyDeps = Agg(ivy"com.lihaoyi::utest:0.7.3")

    def testFrameworks = Seq("utest.runner.Framework")
  }

}

