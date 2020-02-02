import mill._
import mill.modules.Util
import scalalib._
import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import mill.contrib.buildinfo.BuildInfo

object firrtl extends ScalaModule with SbtModule {
  def scalaVersion = "2.12.10"

  def antlr4Version = "4.7.1"

  def protocVersion = "3.5.1"

  override def scalacOptions = Seq(
    "-deprecation",
    "-unchecked",
    "-Yrangepos", // required by SemanticDB compiler plugin
    "-Ywarn-unused-import" // required by `RemoveUnused` rule
  )

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"${scalaOrganization()}:scala-reflect:${scalaVersion()}",
    ivy"com.github.scopt::scopt:3.7.1",
    ivy"net.jcazevedo::moultingyaml:0.4.1",
    ivy"org.json4s::json4s-native:3.6.7",
    ivy"org.apache.commons:commons-text:1.7",
    ivy"org.antlr:antlr4-runtime:4.7.1",
    ivy"com.google.protobuf:protobuf-java:3.5.1"
  )

  override def generatedSources = T {
    generatedAntlr4Source() ++ generatedProtoSources()
  }

  /** antlr4 */

  def antlrSource = T.source {
    millSourcePath / 'src / 'main / 'antlr4 / "FIRRTL.g4"
  }

  def antlr4Jar = T {
    Util.download(s"https://www.antlr.org/download/antlr-$antlr4Version-complete.jar")
  }

  def generatedAntlr4Source = T.sources {
    os.proc("java",
      "-jar", antlr4Jar().path.toString,
      "-o", T.ctx.dest.toString,
      "-lib", antlrSource().path.toString,
      "-package", "firrtl.antlr",
      "-no-listener", "-visitor",
      antlrSource().path.toString
    ).call()
    T.ctx.dest
  }

  /** protoc */

  def protobufSource = T.source {
    millSourcePath / 'src / 'main / 'proto / "firrtl.proto"
  }

  def protocJar = T {
    Util.download(s"https://repo.maven.apache.org/maven2/com/github/os72/protoc-jar/$protocVersion/protoc-jar-$protocVersion.jar")
  }

  def generatedProtoSources = T.sources {
    os.proc("java",
      "-jar", protocJar().path.toString,
      "-I", protobufSource().path / os.up,
      s"--java_out=${T.ctx.dest.toString}",
      protobufSource().path.toString()
    ).call()
    T.ctx.dest / "firrtl"
  }
}

object chisel3 extends CommonModule with SbtModule with BuildInfo {
  override def moduleDeps: Seq[ScalaModule] = Seq(firrtl, coreMacros, chiselFrontend)

  override def scalacOptions = Seq(
    "-deprecation",
    "-explaintypes",
    "-feature",
    "-language:reflectiveCalls",
    "-unchecked",
    "-Xlint:infer-any"
  )

  override def ivyDeps = Agg(
    ivy"com.github.scopt::scopt:3.7.1"
  )

  override def generatedSources = T {
    println("debuging")
    println(generatedBuildInfo())
    Seq(generatedBuildInfo()._2)
  }

  override def buildInfoPackageName = Some("chisel3")

  override def buildInfoMembers: T[Map[String, String]] = T {
    Map(
      "buildInfoPackage" -> artifactName(),
      "version" -> "v3.3",
      "scalaVersion" -> scalaVersion()
    )
  }

  object coreMacros extends CommonModule {
    override def moduleDeps: Seq[ScalaModule] = Seq(firrtl)
  }

  object chiselFrontend extends CommonModule {
    override def moduleDeps: Seq[ScalaModule] = Seq(firrtl, coreMacros)
  }

}

trait CommonModule extends ScalaModule {
  def scalaVersion = "2.12.10"

  override def scalacOptions = Seq("-Xsource:2.11")

  override def moduleDeps: Seq[ScalaModule] = Seq(chisel3)

  private val macroParadise = ivy"org.scalamacros:::paradise:2.1.0"

  override def compileIvyDeps = Agg(macroParadise)

  override def scalacPluginIvyDeps = Agg(macroParadise)
}

object chiseltest extends CommonModule with SbtModule {
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"edu.berkeley.cs::treadle:latest.integration",
    ivy"com.lihaoyi::utest:latest.integration",
    ivy"org.scalatest::scalatest:latest.integration"
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

