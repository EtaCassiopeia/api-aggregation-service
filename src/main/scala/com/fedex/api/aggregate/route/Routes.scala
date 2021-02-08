package com.fedex.api.aggregate.route

import cats.data.Kleisli
import com.fedex.api.aggregate.ApiAggregator.AppTask
import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient.{FedexClient, FedexClientEnv}
import com.fedex.api.client.model._
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.refined._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
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

  private implicit def encoder[A](implicit D: Encoder[A]): EntityEncoder[AppTask, A] = jsonEncoderOf[AppTask, A]

  def aggregatorService(
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
  ): Kleisli[AppTask, Request[AppTask], Response[AppTask]] =
    HttpRoutes
      .of[AppTask] {
        case GET -> Root / "aggregation" :? PricingQueryParamMatcher(pricingQueryParam) +& TrackQueryParamMatcher(
              trackQueryParam
            ) +& ShipmentQueryParamMatcher(shipmentsQueryParam) =>
          (pricingQueryParam, trackQueryParam, shipmentsQueryParam) match {
            case (Right(p), Right(t), Right(s)) =>
              val result = for {
                pricingResponseFiber <- log.info(s"Queuing pricing request with parameters $p") *> pricingQueue(p).fork

                trackResponseFiber <- log.info(s"Queuing track request with parameters $t") *> trackQueue(t).fork

                shipmentsResponseFiber <-
                  log.info(s"Queuing shipments request with parameters $s") *> shipmentsQueue(s).fork

                pricingResponse <- pricingResponseFiber.join
                trackResponse <- trackResponseFiber.join
                shipmentsResponse <- shipmentsResponseFiber.join

              } yield AggregatedResponse(pricingResponse, trackResponse, shipmentsResponse)

              Ok(result)
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
