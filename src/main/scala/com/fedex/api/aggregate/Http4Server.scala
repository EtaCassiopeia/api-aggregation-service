package com.fedex.api.aggregate

import com.fedex.api.aggregate.ApiAggregator.{AppEnvironment, AppTask}
import com.fedex.api.aggregate.config.ServerConfig
import com.fedex.api.aggregate.route.RouteHelper._
import com.fedex.api.aggregate.route.Routes
import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient.{FedexClient, FedexClientEnv}
import com.fedex.api.client.model.{ISOCountyCode, OrderNumber, PriceType, ProductType, TrackStatus}
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.interop.catz._

object Http4Server {

  type Http4Server = Has[Server]

  def createHttp4Server(
    serverConfig: ServerConfig,
    pricingQueue: BulkDike[
      FedexClient with FedexClientEnv,
      Nothing,
      List[ISOCountyCode],
      Map[ISOCountyCode, PriceType]
    ],
    trackQueue: BulkDike[FedexClient with FedexClientEnv, Nothing, List[OrderNumber], Map[OrderNumber, TrackStatus]],
    shipmentsQueue: BulkDike[FedexClient with FedexClientEnv, Nothing, List[OrderNumber], Map[OrderNumber, List[
      ProductType
    ]]]
  ): ZManaged[AppEnvironment, Throwable, Server] =
    ZManaged.runtime[AppEnvironment].flatMap { implicit runtime: Runtime[AppEnvironment] =>
      BlazeServerBuilder[AppTask](runtime.platform.executor.asEC)
        .bindHttp(serverConfig.port, serverConfig.host)
        .withHttpApp(Routes.aggregatorService(pricingQueue, trackQueue, shipmentsQueue))
        .resource
        .toManagedZIO
    }

  def createHttp4sLayer(serverConfig: ServerConfig): ZLayer[AppEnvironment, Throwable, Http4Server] =
    ZLayer.fromManaged {
      for {
        pricingQueue <- pricingBulkDike
        trackQueue <- trackBulkDike
        shipmentsQueue <- shipmentsBulkDike
        server <- createHttp4Server(serverConfig, pricingQueue, trackQueue, shipmentsQueue)
      } yield server
    }

}
