package nescala.boston2015

import java.util.Date

object Schedule {

  sealed trait Slot {
    def time: Date
  }

  case class Open(time: Date) extends Slot

  case class Intro(time: Date) extends Slot

  case class Lunch(time: Date) extends Slot

  case class Break(time: Date) extends Slot

  case class Talk(p: Proposal) extends Slot {
    def time = p.time.get
  }

  case class Close(time: Date) extends Slot

  def slots = (misc ++ Proposal.talks.map(Talk(_))).sortBy(_.time)

  val misc = {
    def time(hour: Int, min: Int = 0) =
       Site.dayOneTime
        .withHourOfDay(hour)
        .withMinuteOfHour(min)
        .toDate
    Seq(
      Open(time(8)),
      Intro(time(8, 50)),
      Break(time(9, 45)),
      Break(time(10, 20)),
      Break(time(11, 25)),
      Break(time(12)),
      Lunch(time(12, 20)),
      Break(time(12 + 2, 20)),
      Break(time(12 + 3, 10)),
      Break(time(12 + 3, 30)),
      Break(time(12 + 4, 20)),
      Break(time(12 + 4, 55)),
      Close(time(12 + 5, 30)))
  }
}
