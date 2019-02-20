package com.evolutiongaming.kafka.journal.eventual.cassandra

import com.evolutiongaming.scassandra.TableName

final case class Schema(
  journal: TableName,
  head: TableName,
  pointer: TableName,
  setting: TableName)