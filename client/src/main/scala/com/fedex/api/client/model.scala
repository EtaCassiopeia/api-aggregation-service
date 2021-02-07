package com.fedex.api.client

import eu.timepit.refined.W
import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.string.MatchesRegex
import io.bartholomews.iso_country.CountryCodeAlpha2
import io.circe._

object model {
  import enumeratum.values._

  type OrderNumber = String Refined MatchesRegex[W.`"^[0-9]{9}$"`.T]
  object OrderNumber extends RefinedTypeOps[OrderNumber, String]

  type ProductType = String
  type PriceType = Float Refined Interval.Closed[0f, 100f]
  object PriceType extends RefinedTypeOps[PriceType, Float]

  type ISOCountyCode = String Refined MatchesRegex[W.`"^[A-Z]{2}$"`.T]
  object ISOCountyCode extends RefinedTypeOps[ISOCountyCode, String]

  sealed abstract class TrackStatus(val value: String) extends StringEnumEntry

  case object TrackStatus extends StringEnum[TrackStatus] {
    val values = findValues

    case object NEW extends TrackStatus(value = "NEW")
    case object IN_TRANSIT extends TrackStatus(value = "IN TRANSIT")
    case object COLLECTING extends TrackStatus(value = "COLLECTING")
    case object COLLECTED extends TrackStatus(value = "COLLECTED")
    case object DELIVERING extends TrackStatus(value = "DELIVERING")
    case object DELIVERED extends TrackStatus(value = "DELIVERED")

  }

  implicit val isoCountyCodeEncoder: Encoder[ISOCountyCode] = c => Json.fromString(c.value)
  implicit val isoCountyCodeDecoder: Decoder[ISOCountyCode] = Decoder.decodeString.emap(str =>
    CountryCodeAlpha2.values
      .find(_.value == str)
      .map(c => ISOCountyCode.unsafeFrom(c.value))
      .toRight(s"Invalid ISO_3166-1 code: [$str]")
  )

  implicit val trackStatusEncoder: Encoder[TrackStatus] = c => Json.fromString(c.value)
  implicit val trackStatusDecoder: Decoder[TrackStatus] =
    Decoder.decodeString.emap(str => TrackStatus.values.find(_.value == str).toRight(s"Invalid track status: [$str]"))
}
