package com.fedex.api.http

import scala.util.control.NoStackTrace

sealed trait HttpClientError extends Throwable with NoStackTrace

final case class GenericHttpError(message: String) extends Throwable(message) with HttpClientError
final case class TooManyRequests(message: String) extends Throwable(message) with HttpClientError
final case class RequestTimedOut(message: String) extends Throwable(message) with HttpClientError
