package com.evolutiongaming.kafka.journal.eventual.cassandra


import java.lang.{Long => LongJ}
import java.time.Instant
import java.util.Date

import com.evolutiongaming.cassandra.CassandraHelper._
import com.evolutiongaming.concurrent.async.Async
import com.evolutiongaming.kafka.journal.SeqNr.Helper._
import com.evolutiongaming.kafka.journal.{Key, SeqNr}


object MetadataStatement {

  def createTable(name: TableName): String = {
    s"""
       |CREATE TABLE IF NOT EXISTS ${ name.asCql } (
       |id text,
       |topic text,
       |partition int,
       |offset bigint,
       |segment_size int,
       |seq_nr bigint,
       |delete_to bigint,
       |created timestamp,
       |updated timestamp,
       |properties map<text,text>,
       |PRIMARY KEY ((topic), id))
       |""".stripMargin
  }


  object Insert {
    type Type = (Key, Metadata, Instant) => Async[Unit]

    def apply(name: TableName, session: PrepareAndExecute): Async[Type] = {

      val query =
        s"""
           |INSERT INTO ${ name.asCql } (id, topic, segment_size, seq_nr, delete_to, created, updated, properties)
           |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
           |""".stripMargin

      for {
        prepared <- session.prepare(query)
      } yield {
        (key: Key, metadata: Metadata, timestamp: Instant) =>
          val bound = prepared
            .bind()
            .encode("id", key.id)
            .encode("topic", key.topic)
            .encode("segment_size", metadata.segmentSize)
            .encode("seq_nr", metadata.seqNr)
            .encode("delete_to", metadata.deleteTo)
            .encode("created", timestamp)
            .encode("updated", timestamp)
          session.execute(bound).unit
      }
    }
  }


  object Select {
    type Type = Key => Async[Option[Metadata]]

    def apply(name: TableName, session: PrepareAndExecute): Async[Type] = {
      val query =
        s"""
           |SELECT segment_size, seq_nr, delete_to FROM ${ name.asCql }
           |WHERE id = ?
           |AND topic = ?
           |""".stripMargin

      for {
        prepared <- session.prepare(query)
      } yield {
        key: Key =>
          val bound = prepared.bind(key.id, key.topic)
          for {
            result <- session.execute(bound)
          } yield for {
            row <- Option(result.one()) // TODO use CassandraSession wrapper
          } yield {
            Metadata(
              segmentSize = row.decode[Int]("segment_size"),
              seqNr = row.decode[SeqNr]("seq_nr"),
              deleteTo = row.decode[Option[SeqNr]]("delete_to"))
          }
      }
    }
  }

  object Update {
    type Type = (Key, Option[SeqNr], Instant) => Async[Unit]

    def apply(name: TableName, session: PrepareAndExecute): Async[Type] = {
      val query =
        s"""
           |UPDATE ${ name.asCql }
           |SET delete_to = ?, updated = ?
           |WHERE id = ?
           |AND topic = ?
           |""".stripMargin

      for {
        prepared <- session.prepare(query)
      } yield {
        (key: Key, deleteTo: Option[SeqNr], timestamp: Instant) =>
          // TODO avoid casting via providing implicit converters
          val bound = prepared
            .bind(deleteTo.toLong: LongJ, Date.from(timestamp), key.id, key.topic)

          //          val bound = prepared
          //            .bind()
          //            .setLong(0, deleteTo: LongJ)
          //            .setTimestamp(1, Date.from(timestamp))
          //            .setString(2, key.id)
          //            .setString(3, key.topic)

          session.execute(bound).unit
      }
    }
  }
}
