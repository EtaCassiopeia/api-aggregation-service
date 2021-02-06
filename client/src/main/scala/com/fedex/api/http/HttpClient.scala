package com.fedex.api.http

import io.circe
import io.circe.Decoder
import sttp.client
import sttp.client.asynchttpclient.zio.SttpClient
import sttp.client.circe.asJson
import sttp.client.{ResponseError, basicRequest}
import sttp.model.{HeaderNames, StatusCode, Uri}
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.{LogLevel, Logging, log}
import zio._

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

      private def enrichedRequest(extraParams: Map[String, String] = Map.empty) =
        basicRequest
          .headers(
            Map(
              HeaderNames.ContentType -> "application/json; charset=utf-8",
              HeaderNames.Accept -> "application/json; charset=utf-8"
            ) ++ extraParams
          )

      private def sendRequest[Response](
        request: client.Request[Either[ResponseError[circe.Error], Response], Nothing]
      ): ZIO[HttpClientEnv, HttpClientError, Response] =
        log(LogLevel.Info)(s"Sending request: " + request.toCurl) *>
          SttpClient
            .send(request)
            .timeoutFail(RequestTimedOut(s"Request timeout: $request"))(30.seconds)
            .reject {
              case r if r.code == StatusCode.TooManyRequests => TooManyRequests(r.toString())
            }
            .map(_.body)
            .absolve
            .bimap(err => GenericHttpError(err.getMessage), identity)

      override def get[Response: Decoder](
        uri: Uri,
        query: String
      ): ZIO[HttpClientEnv, HttpClientError, Response] = {

        val getRequest = enrichedRequest()
          .response(asJson[Response])
          .get(uri.param("q", query))

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
