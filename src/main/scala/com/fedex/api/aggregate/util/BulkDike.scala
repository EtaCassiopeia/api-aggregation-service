package com.fedex.api.aggregate.util

import cats.kernel.Semigroup
import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.Logging
import zio.stream.ZStream
import cats.implicits._

trait BulkDike[R, E, I, A] {
  def apply(query: I): ZIO[Any, E, A]
}

object BulkDike {

  private final case class State(enqueued: Int, inFlight: Int) {
    val total = enqueued + inFlight
    def enqueue: State = copy(enqueued + 1)
    def startBatchProcess(n: Int): State = copy(enqueued - n, inFlight + n)
    def endProcess: State = copy(enqueued, inFlight - 1)
  }

  def make[R, E, I: Semigroup, A](
    groupedCalls: Int,
    groupedWithinDuration: Duration,
    effect: I => ZIO[R, E, A],
    extractResult: (I, A) => A,
    rejection: => E,
    maxInFlightCalls: Int = 50,
    maxQueueing: Int = 10
  ): ZManaged[R with Logging with Clock, Nothing, BulkDike[R, E, I, A]] =
    for {
      queue <-
        ZQueue
          .bounded[(I, Promise[E, A], Promise[E, Unit])](maxQueueing)
          .toManaged_
      inFlightAndQueued <- Ref.make(State(0, 0)).toManaged_
      _ <-
        ZStream
          .fromQueue(queue)
          .mapConcatM {
            case (input, result, enqueued) =>
              inFlightAndQueued.modify { state =>
                if (state.total < maxQueueing)
                  (enqueued.succeed(()).as(List(input -> result)), state.enqueue)
                else
                  (enqueued.fail(rejection).as(List.empty), state)
              }.flatten
          }
          .groupedWithin(groupedCalls, groupedWithinDuration)
          .mapConcatM { chunk =>
            val queryParamChunk = chunk.map {
              case (query, _) => query
            }
            val fullQueryParams = queryParamChunk.reduce(_ combine _)

            inFlightAndQueued
              .update(_.startBatchProcess(chunk.size)) *> effect(fullQueryParams)
              .fold(
                error => {
                  chunk.map {
                    case (_, result) =>
                      inFlightAndQueued.get
                        .bracket_(inFlightAndQueued.update(_.endProcess), result.fail(error))
                  }
                },
                finalResult => {
                  chunk.map {
                    case (query, result) =>
                      inFlightAndQueued.get
                        .bracket_(
                          inFlightAndQueued.update(_.endProcess),
                          result.succeed(extractResult(query, finalResult))
                        )
                  }
                }
              )
          }
          .buffer(maxQueueing)
          .mapMParUnordered(maxInFlightCalls)(task => task)
          .runDrain
          .fork
          .toManaged_
    } yield new BulkDike[R, E, I, A] {
      override def apply(query: I): ZIO[Any, E, A] =
        for {
          result <- Promise.make[E, A]
          enqueued <- Promise.make[E, Unit]
          resultValue <- for {
            _ <- queue.offer((query, result, enqueued))
            _ <- enqueued.await
            r <- result.await
          } yield r
        } yield resultValue
    }
}
