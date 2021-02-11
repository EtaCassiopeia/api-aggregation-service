package com.fedex.api.client

import com.fedex.api.client.model._
import eu.timepit.refined.auto._
import io.circe.refined._
import io.circe.syntax._
import sttp.client3.Response
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.model.StatusCode
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Task, ULayer, ZIO, ZLayer}

trait FedexClientSpecStub {
  val unavailableOrderNumber: OrderNumber = "123456789"
  val availableOrderNumber: OrderNumber = "123456891"
  val availableOrderNumberWithDelay: OrderNumber = "123456892"

  val expectedProductResponse = Map(availableOrderNumber -> List("envelope"))

  val stubSttp: ULayer[SttpClient] = ZLayer.succeed(
    AsyncHttpClientZioBackend.stub
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${unavailableOrderNumber.value}"))
      .thenRespondWithCode(StatusCode.ServiceUnavailable)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${availableOrderNumber.value}"))
      .thenRespond(expectedProductResponse.asJson.noSpaces)
      .whenRequestMatches(req => req.uri.toString.endsWith(s"shipments?q=${availableOrderNumberWithDelay.value}"))
      .thenRespondF {
        ZIO.sleep(6.seconds).provideLayer(Clock.live) *>
          Task(Response.ok(expectedProductResponse.asJson.noSpaces))
      }
  )
}
