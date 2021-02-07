package com.fedex.api.http

import io.circe
import io.circe.Decoder
import sttp.client3._
import sttp.client3.asynchttpclient.zio._
import sttp.client3.circe._
import sttp.model.{HeaderNames, StatusCode, Uri}
import zio._
import zio.clock.Clock
import zio.logging.{Logging, log}

object HttpClient {

  type HttpClient = Has[Service]
  type HttpClientEnv = SttpClient with Clock with Logging

  trait Service {
    def get[Response: Decoder](
      uri: Uri,
      query: String
    ): ZIO[HttpClientEnv, HttpClientError, Response]
  }

  object Service {
    val live: Service = new Service {

      private val enrichedRequest =
        basicRequest
          .headers(
            Map(
              HeaderNames.ContentType -> "application/json; charset=utf-8",
              HeaderNames.Accept -> "application/json; charset=utf-8"
            )
          )

      private def sendRequest[Response](
        request: Request[Either[ResponseException[String, circe.Error], Response], Any]
      ): ZIO[HttpClientEnv, HttpClientError, Response] =
        log.info(s"Sending request: " + request.toCurl) *>
          send(request)
            //back-end services are delivered with an SLA guaranteeing a response time of at most 5 seconds
            .disconnect
            .timeoutFail(RequestTimedOut(s"Request timeout: $request"))(new DurationSyntax(5).seconds)
            .reject {
              case r if r.code == StatusCode.ServiceUnavailable => ServiceUnavailable(r.toString())
              case r if r.code == StatusCode.BadRequest         => BadRequest(r.toString())
              case r if r.code != StatusCode.Ok                 => GenericHttpError(r.toString())
            }
            .map(_.body)
            .absolve
            .mapError {
              case httpError: HttpClientError => httpError
              case ex: Throwable =>
                GenericHttpError(ex.getMessage)
            }

      override def get[Response: Decoder](
        uri: Uri,
        query: String
      ): ZIO[HttpClientEnv, HttpClientError, Response] = {

        val getRequest = enrichedRequest
          .get(uri.addParam("q", query))
          .response(asJson[Response])

        sendRequest(getRequest)
      }
    }
  }

  def live: ULayer[Has[Service]] =
    ZLayer.succeed(Service.live)

  def get[Response: Decoder](
    uri: Uri,
    query: String
  ): ZIO[HttpClient with HttpClientEnv, HttpClientError, Response] =
    ZIO.accessM(_.get.get(uri, query))

}
