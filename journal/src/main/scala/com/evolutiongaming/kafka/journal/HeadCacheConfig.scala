package com.evolutiongaming.kafka.journal

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

import scala.concurrent.duration._

final case class HeadCacheConfig(
  timeout: FiniteDuration = 3.seconds,
  pollTimeout: FiniteDuration = 10.millis,
  cleanInterval: FiniteDuration = 1.second,
  maxSize: Int = 100000)


object HeadCacheConfig {

  val default: HeadCacheConfig = HeadCacheConfig()

  implicit val configReaderHeadCacheConfig: ConfigReader[HeadCacheConfig] = deriveReader
}