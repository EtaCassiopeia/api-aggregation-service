package com.fedex.api.client

import enumeratum.values._

sealed abstract class TrackingStatus(val value: String) extends StringEnumEntry

case object TrackingStatus extends StringEnum[TrackingStatus] with StringCirceEnum[TrackingStatus] {
  val values = findValues

  case object NEW extends TrackingStatus(value = "NEW")
  case object IN_TRANSIT extends TrackingStatus(value = "IN TRANSIT")
  case object COLLECTING extends TrackingStatus(value = "COLLECTING")
  case object COLLECTED extends TrackingStatus(value = "COLLECTED")
  case object DELIVERING extends TrackingStatus(value = "DELIVERING")
  case object DELIVERED extends TrackingStatus(value = "DELIVERED")

}
