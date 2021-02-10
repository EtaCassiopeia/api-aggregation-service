package com.fedex.api.aggregate

import com.fedex.api.client.model.{ISOCountyCode, OrderNumber}
import sttp.client3.Response
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.model.StatusCode
import zio.{Task, ULayer, ZIO, ZLayer}
import zio.clock.Clock
import eu.timepit.refined.auto._
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import zio.duration.durationInt

trait ApiAggregatorSpecStub {
  val unavailableOrderNumber: OrderNumber = "123456789"
  val availableOrderNumber: OrderNumber = "123456891"
  val availableOrderNumberWithDelay: OrderNumber = "123456892"

  val expectedShipmentResponse = Map(availableOrderNumber -> List("envelope"))
  val expectedTrackResponse = Map(availableOrderNumber -> "NEW")
  val expectedPricingResponse = Map(ISOCountyCode.unsafeFrom("NL") -> 14.24f)
  val expectedPricingResponseWithDelay = Map(ISOCountyCode.unsafeFrom("CN") -> 15.24f)

  val stubSttp: ULayer[SttpClient] = ZLayer.succeed(
    AsyncHttpClientZioBackend.stub
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${unavailableOrderNumber.value}"))
      .thenRespondWithCode(StatusCode.ServiceUnavailable)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${availableOrderNumber.value}"))
      .thenRespond(expectedShipmentResponse.asJson.noSpaces)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${availableOrderNumberWithDelay.value}"))
      .thenRespondF {
        ZIO.sleep(6.seconds).provideLayer(Clock.live) *>
          Task(Response.ok(expectedShipmentResponse.asJson.noSpaces))
      }
      .whenRequestMatches(req => req.uri.toString.endsWith(s"track?q=${availableOrderNumber.value}"))
      .thenRespond(expectedTrackResponse.asJson.noSpaces)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"pricing?q=NL"))
      .thenRespond(expectedPricingResponse.asJson.noSpaces)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"pricing?q=CN"))
      .thenRespondF {
        ZIO.sleep(6.seconds).provideLayer(Clock.live) *>
          Task(Response.ok(expectedPricingResponseWithDelay.asJson.noSpaces))
      }
  )
}
