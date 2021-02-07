import sbt._

object Dependencies {

  object Versions {
    val zioVersion = "1.0.4-2"
    val zioLoggingVersion = "0.5.6"
    val zioCatsInteropVersion = "2.2.0.1"
    val https4Version = "1.0-232-85dadc2"
    val pureConfigVersion = "0.14.0"
    val circeVersion = "0.13.0"
    val sttpVersion = "3.1.0"
    val scalaUriVersion = "2.2.2"
    val log4jVersion = "2.13.1"
    val scalaTestVersion = "3.2.2"
    val kindProjector = "0.10.3"
    val refinedVersion = "0.9.20"
    val enumeratumVersion = "1.6.1"
    val scalaIsoVersion = "0.1.2"
  }

  object Libraries {

    import Versions._

    val zio = "dev.zio" %% "zio" % zioVersion
    val zioCatsInterop = "dev.zio" %% "zio-interop-cats" % zioCatsInteropVersion
    val zioLogging = "dev.zio" %% "zio-logging" % zioLoggingVersion
    val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion

    private val http4s: String => ModuleID = artifact => "org.http4s" %% artifact % https4Version
    val http4sModules = Seq("http4s-blaze-server", "http4s-dsl", "http4s-circe").map(http4s)

    private val log4j: String => ModuleID = artifact => "org.apache.logging.log4j" % artifact % log4jVersion

    val log4jModules: Seq[sbt.ModuleID] = Seq("log4j-api", "log4j-core", "log4j-slf4j-impl").map(log4j)

    private val sttpModule: String => ModuleID = artifact => "com.softwaremill.sttp.client3" %% artifact % sttpVersion

    val sttpModules: Seq[sbt.ModuleID] = Seq("core", "async-http-client-backend-zio", "circe").map(sttpModule)

    private val circeModule: String => ModuleID = artifact => "io.circe" %% artifact % circeVersion

    val circeModules: Seq[sbt.ModuleID] =
      Seq("circe-core", "circe-parser", "circe-generic", "circe-generic-extras", "circe-shapes", "circe-refined").map(
        circeModule
      )

    val scalaUri = "io.lemonlabs" %% "scala-uri" % scalaUriVersion

    val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion

    val refined = "eu.timepit" %% "refined" % refinedVersion

    val scalaIso = "io.bartholomews" %% "scala-iso" % scalaIsoVersion

    val enumeratumModules =
      Seq(
        "com.beachape" %% "enumeratum" % enumeratumVersion,
        "com.beachape" %% "enumeratum-circe" % enumeratumVersion
      )

    object test {
      val zioTest = "dev.zio" %% "zio-test" % zioVersion % Test
      val zioTestSbt = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
      val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.2" % Test
    }
  }

}
