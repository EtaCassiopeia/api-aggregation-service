package com.fedex.api.aggregate

import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import com.fedex.api.client.model._
import io.bartholomews.iso_country.CountryCodeAlpha2
import org.http4s._
import org.http4s.dsl.io.QueryParamDecoderMatcher

import scala.util.Either

package object route {

  private def collectErrors[A, B](xs: List[Either[A, B]]): Either[List[A], List[B]] =
    xs.traverse(_.left.map(List(_)).toValidated).toEither

  implicit val orderNumberQueryParamDecoder: QueryParamDecoder[Either[List[String], List[OrderNumber]]] =
    QueryParamDecoder[String].map { param =>
      collectErrors(param.split(",").toList.map(OrderNumber.from))
    }

  implicit val countryCodeQueryParamDecoder: QueryParamDecoder[Either[List[String], List[ISOCountyCode]]] =
    QueryParamDecoder[String].map { param =>
      collectErrors(
        param
          .split(",")
          .toList
          .map(countryCode =>
            CountryCodeAlpha2
              .withValueEither(countryCode)
              .flatMap(c => ISOCountyCode.from(c.value))
              .leftMap(_ => s"$countryCode is not a valid country code")
          )
      )
    }

  object ShipmentQueryParamMatcher
      extends QueryParamDecoderMatcher[Either[List[String], List[OrderNumber]]]("shipments")

  object TrackQueryParamMatcher extends QueryParamDecoderMatcher[Either[List[String], List[OrderNumber]]]("track")

  object PricingQueryParamMatcher extends QueryParamDecoderMatcher[Either[List[String], List[ISOCountyCode]]]("pricing")

}
