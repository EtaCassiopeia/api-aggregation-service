package com.fedex.api.client

import eu.timepit.refined.string.Url
import pureconfig.ConfigSource
import zio.{Task, ZIO}
import io.circe.refined._
import pureconfig._
import pureconfig.generic.auto._

case class AppConfig(baseUrl: Url)

object AppConfig {
  def load(): Task[AppConfig] =
    ZIO.effect {
      ConfigSource.default.at("services").loadOrThrow[AppConfig]
    }
}
