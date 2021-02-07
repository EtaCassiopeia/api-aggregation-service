import Dependencies._
import Settings.{commonSettings, compilerSettings, dockerSettings, sbtSettings}

lazy val client = project
  .settings(commonSettings: _*)
  .settings(compilerSettings: _*)
  .settings(sbtSettings: _*)
  .settings(
    name := "tnt-client",
    libraryDependencies ++= Seq(
      Libraries.zio,
      Libraries.zioLogging,
      Libraries.zioLoggingSlf4j,
      Libraries.scalaUri,
      Libraries.refined,
      Libraries.pureConfig,
      Libraries.scalaIso,
      Libraries.test.zioTest,
      Libraries.test.zioTestSbt,
      Libraries.test.scalaCheck
    ) ++ Libraries.circeModules ++ Libraries.sttpModules ++ Libraries.enumeratumModules
  )

lazy val root = (project in file("."))
  .aggregate(client)
  .dependsOn(client)
  .settings(
    name := "tnt-api-aggregation-service"
  )
  .settings(commonSettings: _*)
  .settings(compilerSettings: _*)
  .settings(sbtSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Libraries.zioCatsInterop
    ) ++ Libraries.http4sModules,
    mainClass in Compile := Some("com.fedex.api.aggregate.ApiAggregator")
  )
  .enablePlugins(
    JavaAppPackaging,
    DockerPlugin
  )
