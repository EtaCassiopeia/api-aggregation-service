package com.fedex.api.client

import pureconfig.ConfigSource
import zio.{Task, ZIO}
import pureconfig.generic.auto._

case class ClientConfig(backendHost: String, backendPort: Int)

object ClientConfig {

  def loadOrThrow(): ClientConfig = ConfigSource.default.at("services").loadOrThrow[ClientConfig]

  def load(): Task[ClientConfig] = ZIO.effect(loadOrThrow())
}
