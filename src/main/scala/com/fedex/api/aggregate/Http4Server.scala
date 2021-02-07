package com.fedex.api.aggregate

import com.fedex.api.aggregate.ApiAggregator.{AppEnvironment, AppTask}
import com.fedex.api.aggregate.config.ServerConfig
import com.fedex.api.aggregate.route.Routes
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.interop.catz._

object Http4Server {

  type Http4Server = Has[Server]

  def createHttp4Server(serverConfig: ServerConfig): ZManaged[AppEnvironment, Throwable, Server] =
    ZManaged.runtime[AppEnvironment].flatMap { implicit runtime: Runtime[AppEnvironment] =>
      BlazeServerBuilder[AppTask](runtime.platform.executor.asEC)
        .bindHttp(serverConfig.port, serverConfig.host)
        .withHttpApp(Routes.aggregatorService)
        .resource
        .toManagedZIO
    }

  def createHttp4sLayer(serverConfig: ServerConfig): ZLayer[AppEnvironment, Throwable, Http4Server] =
    ZLayer.fromManaged(createHttp4Server(serverConfig))

}
