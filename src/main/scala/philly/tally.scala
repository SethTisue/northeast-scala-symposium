package nescala.philly

import nescala.{ AuthorizedToken, Cached, Clock, Meetup, Store }

object Tally extends Templates {
  import unfiltered._
  import unfiltered.request._
  import unfiltered.response._
  import QParams._

  def talks(
    req: HttpRequest[Any],
    pathVars: Map[String, String]
  ) = req match {
    case r @ GET(_) =>
      r match {
        case AuthorizedToken(t) =>
          val mid = t.memberId.get
          if(hosting(mid)) talliedFor("proposals")
          else Redirect("/2013/talks")
        case _ =>
          Redirect("/2013/talks")
      }
    case _ => Pass
  }

  private def talliedFor(kind: String) =
    Store { s =>
      val Talkr = """philly:%s:(.*):.*""".format(kind).r
      val ks = s.keys("philly:%s:*:*".format(kind)).getOrElse(Nil).flatten
      val entries: Seq[Map[String, String]] = (List.empty[Map[String, String]] /: ks)((a,e) =>
        s.hmget(e, "votes", "name").get ++ Map("id"-> e) :: a
      ).map { talk =>
        talk("id") match {
          case Talkr(who) => talk ++ s.hmget(
            "philly:members:%s" format who, "mu_name", "mu_photo").get
        }
      }
      val total = entries.map(_("votes").toInt).sum
      tallied(true, total, entries.sortBy(_("votes").toInt).reverse, kind)
    }

  private def hosting(who: String) = Meetup.hosting(who, Meetup.Philly.eventId)
}
