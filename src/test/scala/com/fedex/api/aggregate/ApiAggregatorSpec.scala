package com.fedex.api.aggregate

import com.fedex.api.aggregate.ApiAggregator.AppTask
import com.fedex.api.aggregate.route.RouteHelper._
import com.fedex.api.aggregate.route.Routes
import com.fedex.api.client.FedexClient
import com.fedex.api.http.HttpClient
import org.http4s._
import org.http4s.implicits._
import zio.Fiber
import zio.clock.currentTime
import zio.console.putStrLn
import zio.duration.durationInt
import zio.interop.catz._
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, _}

import java.util.concurrent.TimeUnit

object ApiAggregatorSpec extends DefaultRunnableSpec with ApiAggregatorSpecStub {

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
      testM("should handle optional query parameters") {
        val io = for {
          responseFiber <-
            routes
              // `shipments` is missing
              .use(
                _.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891"))
              )
              .fork
          _ <- TestClock.adjust(5.seconds)
          response <- responseFiber.join
          b <- response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
        } yield b
        assertM(io.provideSomeLayer(testLayer))(
          equalTo("""{"pricing":{"NL":14.24},"track":{"123456891":"NEW"},"shipments":null}""")
        )
      },
      testM("should return null value in case of any problem") {
        val io = for {
          responseFiber <-
            routes
              // A request to the 'shipments' API with order id = 123456789 causes a timeout error
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
      testM(
        "should return the result within SLA response time even if there isn't enough call for a particular API in the queue"
      ) {
        val testCase = for {
          processTime <-
            routes
              .use(r =>
                for {
                  startTime <- currentTime(TimeUnit.SECONDS)
                  //There is only one call to Shipments API, although there are enough cap for `pricing` and `track` queues
                  //The service must wait for the Shipments API to be called, which will happen after 5 seconds
                  f1 <-
                    r.run(
                      Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891&shipments=123456789")
                    ).fork
                  f2 <- r.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891")).fork
                  f3 <- r.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891")).fork
                  f4 <- r.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891")).fork
                  f5 <- r.run(Request[AppTask](Method.GET, uri"/aggregation?pricing=NL&track=123456891")).fork
                  _ <- TestClock.adjust(5.seconds)
                  _ <- Fiber.joinAll(Array(f1, f2, f3, f4, f5))
                  endTime <- currentTime(TimeUnit.SECONDS)
                  processTime = endTime - startTime
                  _ <- putStrLn(s"took $processTime seconds to get the results")
                } yield processTime
              )
        } yield assert(processTime)(isLessThanEqualTo(5L))

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      },
      testM(
        "should immediately return a response as soon as a cap of 5 calls for an individual API is reached"
      ) {
        val testCase = for {
          processTime <-
            routes
              .use(r =>
                for {
                  startTime <- currentTime(TimeUnit.SECONDS)
                  f <-
                    r.run(
                      Request[AppTask](Method.GET, uri"/aggregation?pricing=ZA,CN,CA,NL,BE")
                    ).fork
                  _ <- TestClock.adjust(1.seconds)
                  _ <- f.join
                  endTime <- currentTime(TimeUnit.SECONDS)
                  processTime = endTime - startTime
                  _ <- putStrLn(s"took $processTime seconds to get the results")
                } yield processTime
              )
        } yield assert(processTime)(isLessThanEqualTo(1L))

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      },
      testM("should NOT take more than 10 seconds to return a response in the worst-case scenario") {
        val testCase = for {
          startTime <- currentTime(TimeUnit.SECONDS)
          responseFiber <-
            //- There is only one call in the queue, it takes 5 seconds to send the request to the backend service API
            //- A request to the `shipments` with the order id = `123456789` always return a response after 6 seconds which is greater then the timeout value
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
