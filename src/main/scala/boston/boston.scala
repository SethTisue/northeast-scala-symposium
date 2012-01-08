package nescala.boston

import nescala.{ Cached, Clock, CookieToken, ClientToken,
                Meetup, PollOver, Store, Tally }

object Boston extends Templates {
  import unfiltered.request._
  import unfiltered.response._
  import QParams._

  def api: unfiltered.Cycle.Intent[Any, Any] = {
    case GET(Path(Seg("boston" :: "rsvps" :: event :: Nil))) => Clock("fetching rsvp list for %s" format event) {
      import net.liftweb.json._
      import net.liftweb.json.JsonDSL._

      JsonContent ~> ResponseString(
        Cached.getOr("meetup:event:%s:rsvps" format event) {
          (compact(render(Meetup.rsvps(event))), Some(60 * 15))
        })
    }
  }

  def panels: unfiltered.Cycle.Intent[Any, Any] = {
    case GET(Path(Seg("2012" :: "panels" :: Nil))) => Clock("fetching 2012 panels") {
      val proposals = Store { s =>
        s.keys("boston:panel_proposals:*:*") match {
          case None => Seq.empty[Map[String, String]]
          case Some(keys) =>
            (Seq.empty[Map[String, String]] /: keys.filter(_.isDefined)){
              (a, e) => a ++
                s.hmget[String, String](e.get, "name", "desc").map(_ + ("id" -> e.get))
            }
        }
      }
      val Proposing = """boston:panel_proposals:(.*):(.*)""".r
      val pids = (proposals.map {
        _("id") match {
          case Proposing(who, _) =>
            who
        }
      }).toSet.toSeq

      def mukey(of: String) = "boston:members:%s" format of
      
      // find the members we may not have cached yet
      val notcached = Store { s =>
        pids.filterNot(p => s.exists(mukey(p)))
      }
      val members = if(!notcached.isEmpty) {
        // query meetup for members
        // we don't have cached yet
        val ms = Meetup.members(notcached)
        Store { s =>
          ms.map { m =>
            val data = Map(
              "mu_name" -> m.name, "mu_photo" -> m.photo
            ) ++  m.twttr.map("twttr" -> _)
            s.hmset(mukey(m.id), data)
            m.id -> data
          }
        }
      } else {
        // load up cached members
        Store { s =>
          pids.map { p =>
            p -> s.hmget[String, String](mukey(p), "mu_name", "mu_photo", "twttr").get
          }
        }
      }
      val ret = (proposals /: members)((a, e) => e match {
        case (key, value) =>          
          val (matching, notmatching) = a.partition(_("id").matches("""boston:panel_proposals:%s:(.*)""".format(key)))
          matching.map(_ ++ value) ++ notmatching
      })
      panelListing(scala.util.Random.shuffle(ret))
    }
  }

  def talks: unfiltered.Cycle.Intent[Any, Any] = {
    case GET(Path(Seg("2012" :: "talks" :: Nil))) => Clock("fetching 2012 talks") {
      val proposals = Store { s =>
        s.keys("boston:proposals:*:*") match {
          case None => Seq.empty[Map[String, String]]
          case Some(keys) =>
            (Seq.empty[Map[String, String]] /: keys.filter(_.isDefined)){
              (a, e) => a ++
                s.hmget[String, String](e.get, "name", "desc").map(_ + ("id" -> e.get))
            }
        }
      }
      val Proposing = """boston:proposals:(.*):(.*)""".r
      val pids = (proposals.map {
        _("id") match {
          case Proposing(who, _) =>
            who
        }
      }).toSet.toSeq

      def mukey(of: String) = "boston:members:%s" format of
      val notcached = Store { s =>
        pids.filterNot(p => s.exists(mukey(p)))
      }
      val members = if(!notcached.isEmpty) {
        val ms = Meetup.members(notcached)
        Store { s =>
          ms.map { m =>
            val data = Map(
              "mu_name" -> m.name, "mu_photo" -> m.photo
            ) ++  m.twttr.map("twttr" -> _)
            s.hmset(mukey(m.id), data)
            m.id -> data
          }
        }
      } else {
        Store { s =>
          pids.map { p =>
            p -> s.hmget[String, String](mukey(p), "mu_name", "mu_photo", "twttr").get
          }
        }
      }
      val ret = (proposals /: members)((a, e) => e match {
        case (key, value) =>          
          val (matching, notmatching) = a.partition(_("id").matches("""boston:proposals:%s:(.*)""".format(key)))
          matching.map(_ ++ value) ++ notmatching
      })
      talkListing(scala.util.Random.shuffle(ret))
    }
  }

  def site: unfiltered.Cycle.Intent[Any, Any]  = {

    case GET(Path("/") & CookieToken(ClientToken(_, _, Some(_), Some(mid)))) =>
      // fetch the logged in user's talk proposals
      val proposals = Store { s =>
        s.keys("boston:proposals:%s:*" format mid) match {
          case None => Seq.empty[Map[String, String]]
          case Some(keys) =>
            (Seq.empty[Map[String, String]] /: keys.filter(_.isDefined))(
              (a, e) => a ++
                s.hmget[String, String](e.get, "name", "desc").map(_ + ("id" -> e.get))
            )
        }
      }
      val panels = Store { s =>
        s.keys("boston:panel_proposals:%s:*" format mid) match {
          case None => Seq.empty[Map[String, String]]
          case Some(keys) =>
            (Seq.empty[Map[String, String]] /: keys.filter(_.isDefined))(
              (a, e) => a ++
                s.hmget[String, String](e.get, "name", "desc").map(_ + ("id" -> e.get))
            )
        }
      }

      indexWithAuth(proposals, panels)

    case GET(Path("/")) =>
      indexNoAuth
    
    //case req @Path("/vote") => PollOver.intent(req)
    //case req @Path("/tally") => Tally.intent(req)
  }
}
