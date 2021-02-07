package com.fedex.api.aggregate.route

import cats.data.Kleisli
import com.fedex.api.aggregate.ApiAggregator.AppTask
import com.fedex.api.client.FedexClient._
import com.fedex.api.client.model._
import io.circe.Encoder
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import zio.ZIO
import zio.interop.catz._
import zio.logging.log

case class AggregatedResponse(
  pricing: Map[ISOCountyCode, PriceType],
  track: Map[OrderNumber, TrackStatus],
  shipments: Map[OrderNumber, List[ProductType]]
)

object Routes {

  private val dsl = Http4sDsl[AppTask]
  import dsl._

  implicit def encoder[A](implicit D: Encoder[A]): EntityEncoder[AppTask, A] = jsonEncoderOf[AppTask, A]

  val aggregatorService: Kleisli[AppTask, Request[AppTask], Response[AppTask]] = HttpRoutes
    .of[AppTask] {
      case GET -> Root / "aggregation" :? PricingQueryParamMatcher(pricingQueryParam) +& TrackQueryParamMatcher(
            trackQueryParam
          ) +& ShipmentQueryParamMatcher(shipmentsQueryParam) =>
        (pricingQueryParam, trackQueryParam, shipmentsQueryParam) match {
          case (Right(p), Right(t), Right(s)) =>
            val result = for {
              pricingResponse <- pricing(p: _*).catchAll(error =>
                log.error(error.getMessage) *> ZIO.succeed(Map.empty[ISOCountyCode, PriceType])
              )
              trackResponse <- track(t: _*).catchAll(error =>
                log.error(error.getMessage) *> ZIO.succeed(Map.empty[OrderNumber, TrackStatus])
              )
              shipmentsResponse <- shipments(s: _*).catchAll(error =>
                log.error(error.getMessage) *> ZIO.succeed(Map.empty[OrderNumber, List[ProductType]])
              )
            } yield AggregatedResponse(pricingResponse, trackResponse, shipmentsResponse)

            Ok(log.info(s"Result : ${result.toString}") *> result)
          case _ =>
            BadRequest(
              List(pricingQueryParam, trackQueryParam, shipmentsQueryParam)
                .filter(_.isLeft)
                .flatMap(_.swap.getOrElse(List.empty))
                .mkString(",")
            )
        }

    }
    .orNotFound

}
