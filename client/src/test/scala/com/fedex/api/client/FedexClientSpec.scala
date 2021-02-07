package com.fedex.api.client

import com.fedex.api.client.FedexClient._
import com.fedex.api.http.{HttpClient, RequestTimedOut, ServiceUnavailable}
import eu.timepit.refined.auto._
import io.circe.refined._
import io.circe.syntax._
import sttp.client3.Response
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.model.StatusCode
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.Logging
import zio.test.Assertion.{anything, fails, hasSameElements, isSubtype}
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.{Task, ULayer, ZIO, ZLayer}

object FedexClientSpec extends DefaultRunnableSpec {

  val unavailableOrderNumber: OrderNumber = "123456789"
  val availableOrderNumber: OrderNumber = "123456891"
  val availableOrderNumberWithDelay: OrderNumber = "123456892"

  val expectedProductResponse = Map(availableOrderNumber -> "envelope")

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

  val testLayer =
    HttpClient.live ++ FedexClient.live("localhost:8080") ++ stubSttp ++ Logging.console()

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("FedexClient")(
      testM("should be able to handle errors") {
        val testCase = for {
          res <- products(unavailableOrderNumber).run
        } yield assert(res)(fails(isSubtype[ServiceUnavailable](anything)))

        testCase.provideSomeLayer(testLayer)
      },
      testM("should be able to handle request timeouts") {
        val testCase = for {
          fiber <- products(availableOrderNumberWithDelay).fork
          _ <- TestClock.adjust(5.seconds)
          res <- fiber.join.run
        } yield assert(res)(fails(isSubtype[RequestTimedOut](anything)))

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      },
      testM("should be able to get the response within a reasonable time") {
        val testCase =
          for {
            res <- products(availableOrderNumber)
            productList = res.keys
          } yield assert(productList)(
            hasSameElements(
              List(availableOrderNumber)
            )
          )

        testCase.provideSomeLayer(testLayer)
      }
    )
}
