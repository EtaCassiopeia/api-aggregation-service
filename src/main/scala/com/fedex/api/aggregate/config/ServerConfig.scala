package com.fedex.api.aggregate.config

import pureconfig.ConfigSource
import zio.{Task, ZIO}
import pureconfig.generic.auto._

case class ServerConfig(host: String, port: Int)

object ServerConfig {
  def loadOrThrow(): ServerConfig = ConfigSource.default.at("server").loadOrThrow[ServerConfig]

  def load(): Task[ServerConfig] =
    ZIO.effect(loadOrThrow())
}
