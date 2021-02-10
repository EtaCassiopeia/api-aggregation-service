package com.fedex.api.aggregate.util

import cats.kernel.Semigroup
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.{Logging, log}
import zio.stream.ZStream

trait BulkDike[R, E, I, A] {
  def apply(query: I): ZIO[Any, E, A]
}

object BulkDike {

  def make[R, E, I: Semigroup, A](
    name: String,
    groupedCalls: Int,
    groupedWithinDuration: Duration,
    effect: I => ZIO[R, E, A],
    extractResult: (I, A) => A,
    maxQueueing: Int = 100
  ): ZManaged[R with Logging with Clock, Nothing, BulkDike[R, E, I, A]] =
    for {
      queue <-
        ZQueue
          .bounded[(I, Promise[E, A])](maxQueueing)
          .toManaged_
      _ <-
        ZStream
          .fromQueue(queue)
          .groupedWithin(groupedCalls, groupedWithinDuration)
          .mapConcatM { chunk =>
            val queryParamChunk = chunk.map {
              case (query, _) => query
            }
            val fullQueryParams =
              queryParamChunk.drop(1).foldLeft(queryParamChunk.head)((s, i) => Semigroup[I].combine(s, i))

//            log.info(s"[$name] Running effect with params $fullQueryParams") *>
            effect(fullQueryParams)
//              .tapBoth(
//                error => {
//                  log.error(s"[$name] ${error.toString}")
//                },
//                finalResult => {
//                  log.info(s"[$name] Received response: ${finalResult.toString}")
//                }
//              )
              .fold(
                error => {
                  chunk.map { case (_, result) => result }.map(_.fail(error)).toList
                },
                finalResult => {
                  chunk.map {
                    case (query, result) => result.succeed(extractResult(query, finalResult))
                  }.toList
                }
              )
          }
          .mapM(task => task)
          .runDrain
          .fork
          .toManaged_
    } yield new BulkDike[R, E, I, A] {
      override def apply(query: I): ZIO[Any, E, A] =
        for {
          result <- Promise.make[E, A]
          resultValue <- for {
            _ <- queue.offer((query, result))
            r <- result.await
          } yield r
        } yield resultValue
    }
}
