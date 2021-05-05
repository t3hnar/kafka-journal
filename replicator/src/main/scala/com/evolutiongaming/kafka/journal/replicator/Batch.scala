package com.evolutiongaming.kafka.journal.replicator

import cats.data.{NonEmptyList => Nel}
import cats.syntax.all._
import com.evolutiongaming.kafka.journal._

sealed abstract class Batch extends Product {
  
  def partitionOffset: PartitionOffset
}

object Batch {

  def of(records: Nel[ActionRecord[Action]]): List[Batch] = {

    def cut(appends: Appends, delete: Action.Delete) = {
      val append = appends.records.head.action
      append.range.to <= delete.to.value
    }

    records
      .foldLeft(List.empty[Batch]) { (bs, record) =>
        val partitionOffset = record.partitionOffset

        def appendsOf(records: Nel[ActionRecord[Action.Append]]) = {
          Appends(partitionOffset, records)
        }

        def deleteOf(to: DeleteTo, origin: Option[Origin], version: Option[Version]) = {
          Delete(partitionOffset, to, origin, version)
        }

        def purgeOf(origin: Option[Origin], version: Option[Version]) = {
          Purge(partitionOffset, origin, version)
        }

        def actionRecord[A <: Action](a: A) = record.copy(action = a)

        def origin = {
          bs.foldRight(none[Origin]) { (b, origin) =>
            origin orElse {
              b match {
                case b: Batch.Delete  => b.origin
                case _: Batch.Appends => none
                case b: Batch.Purge   => b.origin
              }
            }
          }
        }

        def version = {
          bs.foldRight(none[Version]) { (b, version) =>
            version orElse {
              b match {
                case b: Batch.Delete  => b.version
                case _: Batch.Appends => none
                case b: Batch.Purge   => b.version
              }
            }
          }
        }

        bs match {
          case b :: tail => (b, record.action) match {
            case (b: Appends, a: Action.Append) =>
              val records = actionRecord(a) :: b.records
              appendsOf(records) :: tail

            case (b: Appends, _: Action.Mark) =>
              appendsOf(b.records) :: tail

            case (b: Appends, a: Action.Delete) =>
              if (cut(b, a)) {
                val delete = deleteOf(a.to, origin orElse a.origin, version orElse a.version)
                delete :: Nil
              } else {
                val delete = deleteOf(a.to, a.origin, a.version)
                delete :: bs
              }

            case (_: Appends, a: Action.Purge) =>
              purgeOf(a.origin, a.version) :: Nil

            case (b: Delete, a: Action.Append) =>
              appendsOf(Nel.of(actionRecord(a))) :: b :: tail

            case (b: Delete, _: Action.Mark) =>
              b.copy(partitionOffset = partitionOffset) :: tail

            case (b: Delete, a: Action.Delete) =>
              if (a.to > b.to) {
                if (tail.collectFirst { case b: Appends => cut(b, a) } getOrElse false) {
                  val delete = deleteOf(a.to, origin orElse a.origin, version orElse a.version)
                  delete :: Nil
                } else {
                  val delete = deleteOf(a.to, b.origin orElse a.origin, b.version orElse a.version)
                  delete :: tail
                }
              } else {
                val delete = b.copy(
                  partitionOffset = partitionOffset,
                  origin = b.origin orElse a.origin,
                  version = b.version orElse a.version)
                delete :: tail
              }

            case (_: Delete, a: Action.Purge) =>
              purgeOf(a.origin, a.version) :: Nil

            case (b: Purge, a: Action.Append) =>
              appendsOf(Nel.of(actionRecord(a))) :: b :: Nil

            case (b: Purge, _: Action.Mark) =>
              b.copy(partitionOffset = partitionOffset) :: Nil

            case (b: Purge, a: Action.Delete) =>
              deleteOf(a.to, a.origin, a.version) :: b :: Nil

            case (_: Purge, a: Action.Purge) =>
              purgeOf(a.origin, a.version) :: Nil
          }

          case Nil =>
            record.action match {
              case a: Action.Append => appendsOf(Nel.of(actionRecord(a))) :: Nil
              case _: Action.Mark   => Nil
              case a: Action.Delete => deleteOf(a.to, a.origin, a.version) :: Nil
              case a: Action.Purge  => purgeOf(a.origin, a.version) :: Nil
            }
        }
      }
      .foldLeft(List.empty[Batch]) { (bs, b) =>
        b match {
          case b: Appends => b.copy(records = b.records.reverse) :: bs
          case b: Delete  => b :: bs
          case b: Purge   => b :: bs
        }
      }
  }


  // TODO expiry: replace partitionOffset with offset
  final case class Appends(
    partitionOffset: PartitionOffset,
    records: Nel[ActionRecord[Action.Append]]
  ) extends Batch


  // TODO expiry: replace partitionOffset with offset
  final case class Delete(
    partitionOffset: PartitionOffset,
    to: DeleteTo,
    origin: Option[Origin],
    version: Option[Version]
  ) extends Batch


  // TODO expiry: replace partitionOffset with offset
  final case class Purge(
    partitionOffset: PartitionOffset,
    origin: Option[Origin],
    version: Option[Version]
  ) extends Batch
}
