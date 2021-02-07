package com.fedex.api.client

import com.fedex.api.client.FedexClient._
import com.fedex.api.http.{BadRequest, HttpClientError}
import io.bartholomews.iso_country.CountryCodeAlpha2
import zio.ZIO
import zio.test.Assertion.{anything, fails, isSubtype}
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object RefinedDataTypeSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("RefinedDataTypes")(
      testM("should not accept order number with less than 9 digits") {
        val testCase = for {
          res <- ZIO.fromEither(OrderNumber.from("123")).mapError(errorMapper).run
        } yield assert(res)(fails(isSubtype[HttpClientError](anything)))

        testCase
      },
      testM("should not accept order number with more than 9 digits") {
        val testCase = for {
          res <- ZIO.fromEither(OrderNumber.from("1234567890")).mapError(errorMapper).run
        } yield assert(res)(fails(isSubtype[HttpClientError](anything)))

        testCase
      },
      testM("should not accept order number with alphabetic characters") {
        val testCase = for {
          res <- ZIO.fromEither(OrderNumber.from("a12345678")).mapError(errorMapper).run
        } yield assert(res)(fails(isSubtype[HttpClientError](anything)))

        testCase
      },
      testM("should not accept an invalid country code") {
        val testCase = for {
          res <- ZIO.effect(CountryCodeAlpha2.withValue("ZZ")).mapError(e => errorMapper(e.getMessage)).run
        } yield assert(res)(fails(isSubtype[HttpClientError](anything)))

        testCase
      }
    )

  val errorMapper: String => HttpClientError = error => BadRequest(error)
}
