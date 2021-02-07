package com.fedex.api.client

import com.fedex.api.http.HttpClient.{HttpClient, HttpClientEnv, _}
import com.fedex.api.http.HttpClientError
import eu.timepit.refined._
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.string._
import io.bartholomews.iso_country.CountryCodeAlpha2
import io.circe.generic.auto._
import io.circe.refined._
import sttp.model.Uri
import zio.{Has, ULayer, ZIO, ZLayer}

object FedexClient {

  type FedexClient = Has[FedexClient.Service]
  type FedexClientEnv = HttpClient with HttpClientEnv

  type OrderNumber = String Refined MatchesRegex[W.`"^[0-9]{9}$"`.T]
  object OrderNumber extends RefinedTypeOps[OrderNumber, String]

  type ProductType = String
  type PriceType = Float Refined Interval.Closed[0f, 100f]
  object PriceType extends RefinedTypeOps[PriceType, Float]

  type ISOCountyCode = CountryCodeAlpha2

  trait Service {
    def products(
      orderNumbers: OrderNumber*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, ProductType]]

    def trackStatus(
      orderNumbers: OrderNumber*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, TrackingStatus]]

    def pricing(
      countryCode: ISOCountyCode*
    ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]]
  }

  object Service {
    def live(baseUrl: Uri): Service =
      new Service {
        override def products(
          orderNumbers: OrderNumber*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, ProductType]] =
          get[Map[OrderNumber, ProductType]](
            baseUrl.addPath("/shipments"),
            orderNumbers.mkString(",")
          )

        override def trackStatus(
          orderNumbers: OrderNumber*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[OrderNumber, TrackingStatus]] =
          get[Map[OrderNumber, TrackingStatus]](
            baseUrl.addPath("/track"),
            orderNumbers.mkString(",")
          )

        override def pricing(
          countryCode: ISOCountyCode*
        ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]] =
          get[Map[ISOCountyCode, PriceType]](
            baseUrl.addPath("/pricing"),
            countryCode.mkString(",")
          )
      }
  }

  def live(baseUrl: String): ULayer[Has[Service]] =
    ZLayer.succeed(Service.live(Uri(baseUrl)))

  def products(
    orderNumbers: OrderNumber*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[OrderNumber, ProductType]] =
    ZIO.accessM(_.get.products(orderNumbers: _*))

  def trackStatus(
    orderNumbers: OrderNumber*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[OrderNumber, TrackingStatus]] =
    ZIO.accessM(_.get.trackStatus(orderNumbers: _*))

  def pricing(
    countryCode: ISOCountyCode*
  ): ZIO[FedexClient with FedexClientEnv, HttpClientError, Map[ISOCountyCode, PriceType]] =
    ZIO.accessM(_.get.pricing(countryCode: _*))

}
