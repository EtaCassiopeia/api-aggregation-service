package com.fedex.api.aggregate.route

import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient._
import com.fedex.api.client.model._
import zio.duration.durationInt
import zio.logging.log

object RouteHelper {
  private val defaultGroupedCalls = 5
  private val defaultGroupedWithinDuration = 5.seconds

  private def pricingEffect(countryCodes: List[ISOCountyCode]) =
    pricing(countryCodes: _*).catchAll(error => log.error(error.getMessage).as(Map.empty[ISOCountyCode, PriceType]))

  private def pricingExtractResult(
    countryCodes: List[ISOCountyCode],
    fullResult: Map[ISOCountyCode, PriceType]
  ): Map[ISOCountyCode, PriceType] = {
    countryCodes.map(cc => cc -> fullResult.getOrElse(cc, PriceType.unsafeFrom(0f))).toMap
  }

  val pricingBulkDike =
    BulkDike.make("Pricing", defaultGroupedCalls, defaultGroupedWithinDuration, pricingEffect, pricingExtractResult)

  private def trackEffect(orderNumbers: List[OrderNumber]) =
    track(orderNumbers: _*).catchAll(error => log.error(error.getMessage).as(Map.empty[OrderNumber, TrackStatus]))

  private def trackExtractResult(
    orderNumbers: List[OrderNumber],
    fullResult: Map[OrderNumber, TrackStatus]
  ): Map[OrderNumber, TrackStatus] = {
    orderNumbers.map(orderNumber => orderNumber -> fullResult.getOrElse(orderNumber, null)).toMap
  }

  val trackBulkDike =
    BulkDike.make("Track", defaultGroupedCalls, defaultGroupedWithinDuration, trackEffect, trackExtractResult)

  private def shipmentsEffect(orderNumbers: List[OrderNumber]) =
    shipments(orderNumbers: _*).catchAll(error =>
      log.error(error.getMessage).as(Map.empty[OrderNumber, List[ProductType]])
    )

  private def shipmentsExtractResult(
    orderNumbers: List[OrderNumber],
    fullResult: Map[OrderNumber, List[ProductType]]
  ): Map[OrderNumber, List[ProductType]] = {
    orderNumbers.map(orderNumber => orderNumber -> fullResult.getOrElse(orderNumber, null)).toMap
  }

  val shipmentsBulkDike =
    BulkDike.make(
      "Shipments",
      defaultGroupedCalls,
      defaultGroupedWithinDuration,
      shipmentsEffect,
      shipmentsExtractResult
    )

}
