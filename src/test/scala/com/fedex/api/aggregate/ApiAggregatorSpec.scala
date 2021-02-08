package com.fedex.api.aggregate

import com.fedex.api.aggregate.ApiAggregator.AppTask
import com.fedex.api.aggregate.route.RouteHelper._
import com.fedex.api.aggregate.route.Routes
import com.fedex.api.client.FedexClient
import com.fedex.api.client.model._
import com.fedex.api.http.HttpClient
import eu.timepit.refined.auto._
import io.circe.generic.auto._
import io.circe.refined._
import io.circe.syntax._
import org.http4s._
import org.http4s.implicits._
import sttp.client3.Response
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import sttp.model.StatusCode
import zio.clock.{Clock, currentTime}
import zio.console.putStrLn
import zio.duration.durationInt
import zio.interop.catz._
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{Task, ULayer, ZIO, ZLayer}

import java.util.concurrent.TimeUnit

object ApiAggregatorSpec extends DefaultRunnableSpec {

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

  val testLayer =
    HttpClient.live ++ FedexClient.live("localhost", 8080) ++ stubSttp ++ Logging.console() ++ TestClock.default

  val routes =
    for {
      pricingQueue <- pricingBulkDike
      trackQueue <- trackBulkDike
      shipmentsQueue <- shipmentsBulkDike
    } yield Routes.aggregatorService(pricingQueue, trackQueue, shipmentsQueue)

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Route suits")(
      testM("should return an aggregated response") {
        val io = for {
          responseFiber <-
            routes
              .use(
                _.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891&shipments=123456891"))
              )
              .fork
          _ <- TestClock.adjust(5.seconds)
          response <- responseFiber.join
          b <- response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
        } yield b
        assertM(io.provideSomeLayer(testLayer))(
          equalTo("""{"pricing":{"NL":14.24},"track":{"123456891":"NEW"},"shipments":{"123456891":["envelope"]}}""")
        )
      },
      testM("should return null value in case of any problem") {
        val io = for {
          responseFiber <-
            routes
              .use(
                _.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891&shipments=123456789"))
              )
              .fork
          _ <- TestClock.adjust(5.seconds)
          response <- responseFiber.join
          b <- response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
        } yield b
        assertM(io.provideSomeLayer(testLayer))(
          equalTo("""{"pricing":{"NL":14.24},"track":{"123456891":"NEW"},"shipments":{"123456789":null}}""")
        )
      },
      testM("should NOT take more than 10 seconds to return a value in the worst-case scenario") {
        val testCase = for {
          startTime <- currentTime(TimeUnit.SECONDS)
          responseFiber <-
            routes
              .use(
                _.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=CN&track=123456891&shipments=123456789"))
              )
              .fork
          _ <- TestClock.adjust(10.seconds)
          _ <- responseFiber.join
          endTime <- currentTime(TimeUnit.SECONDS)
          processTime = endTime - startTime
          _ <- putStrLn(s"took $processTime seconds to get the results")
        } yield assert(processTime)(isLessThanEqualTo(10L))

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      }
    ) @@ sequential
}
