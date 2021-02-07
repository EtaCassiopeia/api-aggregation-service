package com.fedex.api.aggregate.config

import com.fedex.api.client.ClientConfig

case class AppConfig(serverConfig: ServerConfig, clientConfig: ClientConfig)

object AppConfig {
  def loadOrThrow() = AppConfig(ServerConfig.loadOrThrow(), ClientConfig.loadOrThrow())
}
