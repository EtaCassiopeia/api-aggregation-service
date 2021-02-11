package com.fedex.api.aggregate.route

import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient._
import com.fedex.api.client.model._
import com.fedex.api.http.{HttpClientError, TooMayRequests}
import zio.duration.durationInt
import zio.logging.log

object RouteHelper {
  private val defaultGroupedCalls = 5
  private val defaultGroupedWithinDuration = 5.seconds
  private val rejectionError: HttpClientError = TooMayRequests("Too Many Requests")

  private def pricingEffect(countryCodes: List[ISOCountyCode]) =
    pricing(countryCodes: _*).catchAll(error =>
      log.error(error.getMessage).as(Map.empty[ISOCountyCode, Option[PriceType]])
    )

  private def pricingExtractResult(
    countryCodes: List[ISOCountyCode],
    fullResult: Map[ISOCountyCode, Option[PriceType]]
  ): Map[ISOCountyCode, Option[PriceType]] = {
    countryCodes.map(cc => cc -> fullResult.getOrElse(cc, None)).toMap
  }

  val pricingBulkDike =
    BulkDike.make(
      defaultGroupedCalls,
      defaultGroupedWithinDuration,
      pricingEffect,
      pricingExtractResult,
      rejectionError
    )

  private def trackEffect(orderNumbers: List[OrderNumber]) =
    track(orderNumbers: _*).catchAll(error =>
      log.error(error.getMessage).as(Map.empty[OrderNumber, Option[TrackStatus]])
    )

  private def trackExtractResult(
    orderNumbers: List[OrderNumber],
    fullResult: Map[OrderNumber, Option[TrackStatus]]
  ): Map[OrderNumber, Option[TrackStatus]] = {
    orderNumbers.map(orderNumber => orderNumber -> fullResult.getOrElse(orderNumber, None)).toMap
  }

  val trackBulkDike =
    BulkDike.make(defaultGroupedCalls, defaultGroupedWithinDuration, trackEffect, trackExtractResult, rejectionError)

  private def shipmentsEffect(orderNumbers: List[OrderNumber]) =
    shipments(orderNumbers: _*).catchAll(error =>
      log.error(error.getMessage).as(Map.empty[OrderNumber, Option[List[ProductType]]])
    )

  private def shipmentsExtractResult(
    orderNumbers: List[OrderNumber],
    fullResult: Map[OrderNumber, Option[List[ProductType]]]
  ): Map[OrderNumber, Option[List[ProductType]]] = {
    orderNumbers.map(orderNumber => orderNumber -> fullResult.getOrElse(orderNumber, None)).toMap
  }

  val shipmentsBulkDike =
    BulkDike.make(
      defaultGroupedCalls,
      defaultGroupedWithinDuration,
      shipmentsEffect,
      shipmentsExtractResult,
      rejectionError
    )

}
