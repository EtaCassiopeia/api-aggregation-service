package com.fedex.api.client

import com.fedex.api.client.model._
import com.fedex.api.http.HttpClient.{HttpClient, HttpClientEnv, _}
import com.fedex.api.http.HttpClientError
import io.circe.generic.auto._
import io.circe.refined._
import sttp.model.Uri
import zio.{Has, ULayer, ZIO, ZLayer}

object FedexClient {

  type FedexClient = Has[FedexClient.Service]
  type FedexClientEnv = HttpClient with HttpClientEnv

  trait Service {
    def shipments(
      orderNumbers: OrderNumber*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, List[ProductType]]]

    def track(
      orderNumbers: OrderNumber*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, TrackStatus]]

    def pricing(
      countryCode: ISOCountyCode*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]]
  }

  object Service {
    def live(baseUrl: Uri): Service =
      new Service {
        override def shipments(
          orderNumbers: OrderNumber*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, List[ProductType]]] =
          get[Map[OrderNumber, List[ProductType]]](
            baseUrl.addPath("shipments"),
            orderNumbers.mkString(",")
          )

        override def track(
          orderNumbers: OrderNumber*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, TrackStatus]] =
          get[Map[OrderNumber, TrackStatus]](
            baseUrl.addPath("track"),
            orderNumbers.mkString(",")
          )

        override def pricing(
          countryCode: ISOCountyCode*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]] =
          get[Map[ISOCountyCode, PriceType]](
            baseUrl.addPath("pricing"),
            countryCode.map(_.value).mkString(",")
          )
      }
  }

  def live(host: String, port: Int): ULayer[Has[Service]] =
    ZLayer.succeed(Service.live(Uri(host, port)))

  def shipments(
    orderNumbers: OrderNumber*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[OrderNumber, List[ProductType]]] =
    ZIO.accessM(_.get.shipments(orderNumbers: _*))

  def track(
    orderNumbers: OrderNumber*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[OrderNumber, TrackStatus]] =
    ZIO.accessM(_.get.track(orderNumbers: _*))

  def pricing(
    countryCode: ISOCountyCode*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]] =
    ZIO.accessM(_.get.pricing(countryCode: _*))

}
