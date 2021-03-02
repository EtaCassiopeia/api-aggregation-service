package com.fedex.api.aggregate.route

import cats.data.Kleisli
import com.fedex.api.aggregate.ApiAggregator.AppTask
import com.fedex.api.aggregate.BulkDikeType
import com.fedex.api.client.model._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import zio.{IO, UIO, ZIO}
import zio.interop.catz._
import cats.implicits._

case class AggregatedResponse(
  pricing: Option[Map[ISOCountyCode, Option[PriceType]]],
  track: Option[Map[OrderNumber, Option[TrackStatus]]],
  shipments: Option[Map[OrderNumber, Option[List[ProductType]]]]
)

object Routes {

  private val dsl = Http4sDsl[AppTask]
  import dsl._

  private implicit def encoder[A](implicit D: Encoder[A]): EntityEncoder[AppTask, A] = jsonEncoderOf[AppTask, A]

  private def callService[A, O](
    queue: BulkDikeType[List[A], QueryResponse[A, O]],
    parameters: Parameter[A]
  ): UIO[Option[QueryResponse[A, O]]] =
    parameters
      .flatMap(_.toOption)
      .fold(ZIO.succeed(Option.empty[QueryResponse[A, O]])) { parameterList =>
        IO.collectAllPar(parameterList.map(q => queue(List(q))).toSet)
          .map(_.reduce(_ ++ _))
          .asSome
          .catchAll(_ => ZIO.succeed(Option.empty[QueryResponse[A, O]]))
      }

  def aggregatorService(
    pricingQueue: BulkDikeType[List[ISOCountyCode], QueryResponse[ISOCountyCode, PriceType]],
    trackQueue: BulkDikeType[List[OrderNumber], QueryResponse[OrderNumber, TrackStatus]],
    shipmentsQueue: BulkDikeType[List[OrderNumber], QueryResponse[OrderNumber, List[ProductType]]]
  ): Kleisli[AppTask, Request[AppTask], Response[AppTask]] =
    HttpRoutes
      .of[AppTask] {
        case GET -> Root / "aggregation" :? PricingQueryParamMatcher(pricingQueryParam) +& TrackQueryParamMatcher(
              trackQueryParam
            ) +& ShipmentQueryParamMatcher(shipmentsQueryParam) =>
          val providedParameters = List(pricingQueryParam, trackQueryParam, shipmentsQueryParam).flatten
          if (!providedParameters.forall(_.isRight)) {
            BadRequest(
              providedParameters
                .flatMap(_.swap.getOrElse(List.empty))
                .mkString(",")
            )
          } else {
            val result = for {
              pricingResponseFiber <- callService(pricingQueue, pricingQueryParam).fork
              trackResponseFiber <- callService(trackQueue, trackQueryParam).fork
              shipmentsResponseFiber <- callService(shipmentsQueue, shipmentsQueryParam).fork

              aggregatedResponse <- (pricingResponseFiber.join, trackResponseFiber.join, shipmentsResponseFiber.join)
                .mapN(AggregatedResponse)

            } yield aggregatedResponse

            Ok(result)
          }
      }
      .orNotFound
}
