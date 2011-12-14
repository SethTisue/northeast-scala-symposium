package nescala.boston

import nescala.{ Cached, CookieToken, ClientToken,
                Meetup, PollOver, Store, Tally }

object Boston extends Templates {
  import unfiltered.request._
  import unfiltered.response._
  import QParams._

  import net.liftweb.json._
  import net.liftweb.json.JsonDSL._

  val maxProposals = 3

  val Admins = Seq(8157820,7230113)

  def admin: unfiltered.Cycle.Intent[Any, Any] = {
    case GET(Path("/admin/boston")) & CookieToken(ClientToken(_, _, Some(_), Some(mid))) if(Admins.contains(mid.toInt)) =>
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
      admin(proposals)
  }

  def api: unfiltered.Cycle.Intent[Any, Any] = {
    case GET(Path(Seg("boston" :: "rsvps" :: event :: Nil))) =>
      JsonContent ~> ResponseString(
        Cached.getOr("meetup:event:%s:rsvps" format event) {
          (compact(render(Meetup.rsvps(event))), Some(60 * 15))
        })
  }

  def site: unfiltered.Cycle.Intent[Any, Any]  = {

    case GET(Path("/") & CookieToken(ClientToken(_, _, Some(_), Some(mid)))) =>
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
      indexWithAuth(proposals)

    case GET(Path("/")) =>
      indexNoAuth

    case POST(Path("/boston/proposals/withdraw")) & CookieToken(ClientToken(_, _, Some(_), Some(mid))) & Params(p) =>
      val expected = for {
        id <- lookup("id") is required("name is required")
      } yield {
        val key = id.get
        val Withdrawing = """boston:proposals:(.*):(.*)""".r
        (Store { s =>
          if(s.exists(key)) {
            key match {
              case Withdrawing(who, id) =>
                if(mid.equals(who)) {
                  s.del(key).map( stat => if(stat > 0) s.decr(
                    "count:boston:proposals:%s" format who
                  ))
                  Right(key)
                } else Left("not authorized to withdraw this proposal")
              case bk =>
                Left("invalid proposal")
            }
          } else Left("proposal did not exist")
        }).fold({ fail =>
          JsonContent ~> ResponseString("""{"status":400,"msg":"%s"}""" format fail)
        }, { ok =>
          JsonContent ~> ResponseString("""{"status":200,"proposal":"%s"}""" format(ok))
        })
      }
      expected(p) orFail { errors =>
        JsonContent ~> ResponseString("""{"status":400,"msg":"%s"}""" format(
          errors.map { _.error } mkString(". ")
        ))
      }

    case POST(Path("/boston/proposals")) & CookieToken(ClientToken(_, _, Some(_), Some(mid))) & Params(p) =>
      val expected = for {
        name <- lookup("name") is required("name is required")
        desc <- lookup("desc") is required("desc is required")
      } yield {
        val (n, d) = (name.get.trim, desc.get.trim)
        (Store { s =>
          if(n.size > 200 || d.size > 600) Left("Talk contents were too long")
          else {
            // `key` notes :)
            // talks are persisted as keys and values
            // count:{city}:proposals:{memberId} stores the number of proposals a member submitted
            // {city}:proposals:ids stores an atomicly incremented int used to generate proposals ids
            // {city}:proposals:{memberId}:{nextId} stores a map of name, desc, and votes for a proposal
            val mkey = "boston:proposals:%s" format mid
            val ckey = "count:%s" format mkey
            val proposed = s.get(ckey).getOrElse("0").toInt
            if(proposed + 1 > maxProposals) Left("Exceeded max proposals")
            else {
              val nextId = s.incr("boston:proposals:ids").get
              val nextKey = "%s:%s" format(mkey, nextId)
              s.hmset(nextKey, Map(
                "name" -> n,
                "desc" -> d,
                "votes" -> 0
              ))
              Right((s.incr(ckey).get, nextKey))
            }
          }
        }).fold({fail =>
          JsonContent ~> ResponseString("""{"status":400,"msg":"%s"}""" format fail)
        },{ ok =>
          JsonContent ~> ResponseString("""{"status":200,"proposals":%s, "id":"%s"}""" format(ok._1, ok._2))
        })
      }
      expected(p) orFail { errors =>
        JsonContent ~> ResponseString("""{"status":400,"msg":"%s"}""" format(
          errors.map { _.error } mkString(". ")
        ))
      }
    //case req @Path("/vote") => PollOver.intent(req)
    //case req @Path("/tally") => Tally.intent(req)
  }
}
