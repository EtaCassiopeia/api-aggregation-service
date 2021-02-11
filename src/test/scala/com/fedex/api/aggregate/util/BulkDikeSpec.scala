package com.fedex.api.aggregate.util

import com.fedex.api.http.HttpClientError
import zio.{Fiber, ZIO}
import zio.clock.{Clock, currentTime}
import zio.console.Console
import zio.duration.durationInt
import zio.logging.{Logging, log}
import zio.test.Assertion.{anything, fails, hasSameElements, isGreaterThanEqualTo, isSubtype}
import zio.test.TestAspect.sequential
import zio.test.environment.TestClock
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

import java.util.concurrent.TimeUnit

object BulkDikeSpec extends DefaultRunnableSpec {

  private val defaultGroupedCalls = 5
  private val defaultGroupedWithinDuration = 5.seconds

  private def dummyEffect(numbers: List[Int]): ZIO[Logging, Nothing, Map[Int, String]] =
    log.info("Calling dummy effect").as(numbers.map(n => n -> s"Result: $n").toMap)

  private def dummyExtractResult(
    numbers: List[Int],
    fullResult: Map[Int, String]
  ): Map[Int, String] = {
    numbers.map(cc => cc -> fullResult.getOrElse(cc, "Empty")).toMap
  }

  val dummyBulkDike =
    BulkDike.make(
      defaultGroupedCalls,
      defaultGroupedWithinDuration,
      dummyEffect,
      dummyExtractResult,
      "Too Many Requests"
    )

  val lowCapBulkDike =
    BulkDike.make(
      defaultGroupedCalls,
      defaultGroupedWithinDuration,
      dummyEffect,
      dummyExtractResult,
      "Too Many Requests",
      maxInFlightCalls = 1,
      maxQueueing = 1
    )

  val testLayer =
    Logging.console() ++ Clock.live ++ Console.live

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("BulkDike")(
      testM("should be able to immediately return results in case there are enough requests in the queue") {
        val expectedResult = (1 to 5).map(n => n -> s"Result: $n").toMap
        val testCase =
          for {
            res <- dummyBulkDike.use {
              bulkDike =>
                for {
                  firstResFiber <- bulkDike(List(1)).fork
                  secondResFiber <- bulkDike(List(2)).fork
                  thirdResFiber <- bulkDike(List(3)).fork
                  forthResFiber <- bulkDike(List(4)).fork
                  fifthResFiber <- bulkDike(List(5)).fork
                  firstRes <- firstResFiber.join
                  secondRes <- secondResFiber.join
                  thirdRes <- thirdResFiber.join
                  forthRes <- forthResFiber.join
                  fifthRes <- fifthResFiber.join
                } yield firstRes ++ secondRes ++ thirdRes ++ forthRes ++ fifthRes
            }
            keys = res.keys
          } yield assert(keys)(
            hasSameElements(
              expectedResult.keys
            )
          )

        testCase.provideSomeLayer(testLayer)
      },
      testM("should be able to call the effect even if the queue is not full") {
        val testCase =
          dummyBulkDike.use { bulkDike =>
            for {
              startTime <- currentTime(TimeUnit.SECONDS)
              resFiber <- bulkDike(List(1)).fork
              _ <- TestClock.adjust(5.seconds)
              _ <- resFiber.join
              endTime <- currentTime(TimeUnit.SECONDS)
            } yield assert(endTime - startTime)(isGreaterThanEqualTo(5L))
          }

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      },
      testM("should immediately reject the request in case the queue is full") {
        val testCase =
          lowCapBulkDike.use { bulkDike =>
            for {
              _ <- bulkDike(List(1)).fork
              _ <- bulkDike(List(2)).fork
              f3 <- bulkDike(List(3)).fork //This should reject
              result <- f3.join.run
            } yield assert(result)(fails(isSubtype[String](anything)))
          }

        testCase.provideSomeLayer(testLayer ++ TestClock.default)
      }
    ) @@ sequential
}
