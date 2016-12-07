package nescala.nyc2014

import java.util.{ Calendar, Date, TimeZone }
import java.time.{ZoneId, ZonedDateTime}

sealed trait Slot {
  def time: Date // deprecated
  def title: String
  def content: xml.NodeSeq
  // this replaces previous use of ye-old java.util "time"
  def easternTime: ZonedDateTime =
    ZonedDateTime.ofInstant(
      time.toInstant(),
      ZoneId.of("US/Eastern")
    )
}

case class NonPresentation(time: Date, title: String, content: xml.NodeSeq) extends Slot

case class Presentation(proposal: Proposal) extends Slot {
  val time = proposal.time.getOrElse(new Date)
  val title = proposal.name
  val content = <span>{proposal.desc}</span>
  val slides = proposal.slides
  val video = proposal.video
}

case class Schedule(slots: Seq[Slot])

object Schedule {
  def get = {
    val cal = {
      val c = Calendar.getInstance()
      c.setTimeZone(TimeZone.getTimeZone(
        "US/Eastern"))
      c
    }

    def timeAt(hour: Int, min: Int = 0) = {
      cal.set(2014, 3, 2, hour, min, 0)
      cal.getTime
    }

    val slots = (Seq(
      NonPresentation(
        timeAt(8), "Registration", <span>"Grab a coffee and a nametag. Then grab more coffee. Then grab a seat."</span>),
      NonPresentation(
        timeAt(9), "Welcome", <span>"Opening remarks and class begins."</span>),
      NonPresentation(
        timeAt(12, 30), "Lunch", <span>"Disperse for food."</span>),
      NonPresentation(
        timeAt(18), "Foursquare Happy Hour",
        <span>Happy times at { <a href={"https://www.google.com/maps/place/110+Crosby+St/@40.7241392,-73.9968689,17z/data=!3m1!4b1!4m2!3m1!1s0x89c2598f108d4b09:0xc6a04d369a17b693"}>Foursquare HQ</a> }</span>)) ++
      Proposals.talks.map(Presentation(_)))
        .sortBy(_.time)

    Some(Schedule(slots))
  }
}
