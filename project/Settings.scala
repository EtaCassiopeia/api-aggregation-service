import Dependencies._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerPermissionStrategy}
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys.{parallelExecution, _}
import sbt._
import sbt.util.Level

import scala.language.postfixOps

object Settings {

  lazy val compilerSettings =
    Seq(
      scalaVersion := "2.13.4",
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      scalacOptions := Seq(
        "-Ymacro-annotations",
        "-deprecation",
        "-encoding",
        "utf-8",
        "-explaintypes",
        "-feature",
        "-unchecked",
        "-language:postfixOps",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xcheckinit",
        "-Xfatal-warnings"
      ),
      logLevel := Level.Info,
      version := "0.0.0",
      scalafmtOnCompile := true,
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )

  val higherKinds = addCompilerPlugin("org.typelevel" %% "kind-projector" % Versions.kindProjector)

  lazy val sbtSettings =
    Seq(
      fork := true,
      parallelExecution in Test := false,
      cancelable in Global := true
    )

  lazy val commonSettings =
    compilerSettings ++
      sbtSettings ++ Seq(
      organization := "com.fedex",
      resolvers ++= Seq(
        Resolver.mavenLocal,
        "Confluent".at("https://packages.confluent.io/maven/"),
        "jitpack".at("https://jitpack.io"),
        Resolver.jcenterRepo
      ),
      higherKinds
    )

  lazy val dockerSettings =
    Seq(
      dockerBaseImage := "openjdk:8-jdk",
      packageName in Docker := "fedex/api-service-aggregator",
      maintainer in Docker := "Mohsen Zainalpour",
      packageSummary := "API aggregation service",
      packageDescription := "API aggregation service",
      dockerExposedPorts ++= Seq(8181),
      dockerUpdateLatest := true,
      daemonUserUid in Docker := None,
      daemonUser in Docker := "root",
      dockerPermissionStrategy := DockerPermissionStrategy.None
    )
}
