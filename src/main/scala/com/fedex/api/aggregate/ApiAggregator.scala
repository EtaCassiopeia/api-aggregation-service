package com.fedex.api.aggregate

import com.fedex.api.aggregate.Http4Server.Http4Server
import com.fedex.api.aggregate.config.AppConfig
import com.fedex.api.client.FedexClient
import com.fedex.api.client.FedexClient.{FedexClient, FedexClientEnv}
import com.fedex.api.http.HttpClient
import org.http4s.server.Server
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio._
import zio.clock.Clock
import zio.logging.Logging

object ApiAggregator extends App {

  type AppEnvironment = FedexClient with FedexClientEnv
  type AppTask[A] = RIO[AppEnvironment, A]

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {

    val program: ZIO[Has[Server] with AppEnvironment, Nothing, Nothing] =
      ZIO.never

    val appConfig = AppConfig.loadOrThrow()

    val httpServerLayer: ZLayer[AppEnvironment, Throwable, Http4Server] =
      Http4Server.createHttp4sLayer(appConfig.serverConfig)

    val appLayer = AsyncHttpClientZioBackend.layer() ++ HttpClient.live ++ FedexClient.live(
      appConfig.clientConfig.backendHost,
      appConfig.clientConfig.backendPort
    ) ++ Logging.console() ++ Clock.live

    program
      .provideSomeLayer(appLayer ++ (appLayer >>> httpServerLayer))
      .exitCode
  }

}
