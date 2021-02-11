package com.fedex.api.client

import com.fedex.api.client.FedexClient._
import com.fedex.api.http.{HttpClient, RequestTimedOut, ServiceUnavailable}
import zio.duration.durationInt
import zio.logging.Logging
import zio.test.Assertion.{anything, fails, hasSameElements, isSubtype}
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object FedexClientSpec extends DefaultRunnableSpec with FedexClientSpecStub {

  val testLayer =
    HttpClient.live ++ FedexClient.live("localhost", 8080) ++ stubSttp ++ Logging.console()

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("FedexClient")(
      testM("should be able to handle errors") {
        val testCase = for {
          res <- shipments(unavailableOrderNumber).run
        } yield assert(res)(fails(isSubtype[ServiceUnavailable](anything)))

        testCase.provideSomeLayer(testLayer)
      },
      testM("should be able to handle request timeouts") {
        val testCase = for {
          fiber <- shipments(availableOrderNumberWithDelay).fork
          _ <- TestClock.adjust(5.seconds)
          res <- fiber.join.run
        } yield assert(res)(fails(isSubtype[RequestTimedOut](anything)))

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      },
      testM("should be able to get the response within a reasonable time") {
        val testCase =
          for {
            res <- shipments(availableOrderNumber)
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
