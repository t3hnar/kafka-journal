package com.evolutiongaming.kafka.journal

import java.time.Instant

import com.evolutiongaming.kafka.journal.Alias.{SeqNr, Tags}
import com.evolutiongaming.kafka.journal.eventual.PartitionOffset

// TODO add timestamp ?
case class Event(
  seqNr: SeqNr,
  tags: Tags = Tags.Empty,
  payload: Bytes = Bytes.Empty)

// TODO rename
// TODO move to eventual ?
case class ReplicatedEvent(event: Event, timestamp: Instant, partitionOffset: PartitionOffset)

// TODO merge ReplicatedEvent & Replicated ?
case class Replicated[T](value: T, timestamp: Instant, partitionOffset: PartitionOffset)