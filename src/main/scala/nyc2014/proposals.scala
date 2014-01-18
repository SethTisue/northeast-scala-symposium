package nescala.nyc2014

import nescala.{ AuthorizedToken, Cached, Clock, Meetup, Store }
import nescala.request.UrlDecoded
import unfiltered._
import unfiltered.request._
import unfiltered.request.QParams._
import unfiltered.response._

object Proposals extends Templates {
  val TalkTime = 30
  val MaxProposals = 3
  val MaxTalkName = 200
  val MaxTalkDesc = 600

  def errorJson(msg: String) = """{"status":400,"msg":"%s"}""" format msg

  def withdraw(mid: String, key: String) = {
    Votes.withdrawVotesFor(key) match {
      case Nil    => println("talk %s had no votes" format key)
      case votes  => println("remove votes %s for talk %s" format(votes, key))
    }
    val Withdrawing = """nyc2014:proposals:(.*):(.*)""".r
    (Store { s =>
      if (!s.exists(key)) Left("proposal did not exist")
      else {
        key match {
          case Withdrawing(who, id) =>
            if (mid.equals(who)) {
              s.del(key).map( stat => if(stat > 0) s.decr(
                "count:nyc2014:proposals:%s" format who
              ))
              Right(key)
            } else Left("not authorized to withdraw this proposal")
          case bk =>
            Left("invalid proposal")
        }
      }
   })
  }

  def create(mid: String, name: String, desc: String, kind: String) = {
    val (n, d) = (name.trim, desc.trim)
    Store { s =>
      if (n.size > MaxTalkName || d.size > MaxTalkDesc) Left("Talk contents were too long")
      else {
        // `key` notes :)
        // talks are persisted as keys and values
        // count:{city}:proposals:{memberId} stores the number of proposals a member submitted
        // {city}:proposals:ids stores an atomicly incremented int used to generate proposals ids
        // {city}:proposals:{memberId}:{nextId} stores a map of name, desc, and votes for a proposal
        val mkey = "nyc2014:proposals:%s" format mid
        val mukey = "nyc2014:members:%s" format mid
        val ckey = "count:%s" format mkey
        val proposed = s.get(ckey).getOrElse("0").toInt
        if (proposed + 1 > MaxProposals) Left("Exceeded max proposals") else {
          if (!s.exists(mukey)) {
            // cache meetup member data
            Meetup.members(Seq(mid)).headOption match {
              case Some(mem) =>
                s.hmset(mukey, Map(
                  "mu_name" -> mem.name,
                  "mu_photo" -> mem.photo
                ) ++ mem.twttr.map("twttr" -> _))
              case _ =>
                println(s"fetched no meetup members with id $mid")
            }
          }

          val nextId = s.incr("nyc2014:proposals:ids").get
          val nextKey = "%s:%s" format(mkey, nextId)
          s.hmset(nextKey, Map(
            "name" -> n,
            "desc" -> d,
            "kind" -> kind,
            "votes" -> 0
          ))
          Right((s.incr(ckey).get, nextKey))
        }
      }
    }
  }

  def currentProposals = {
    val proposals = Store { s =>
      s.keys("nyc2014:proposals:*:*") match {
        case None => Seq.empty[Map[String, String]]
        case Some(keys) =>
          (Seq.empty[Map[String, String]] /: keys.filter(_.isDefined)){
            (a, e) => a ++
              s.hmget[String, String](e.get,
                                      "name",
                                      "desc",
                                      "kind")
                .map(_ + ("id" -> e.get))
          }
      }
    }

    val Proposing = """nyc2014:proposals:(.*):(.*)""".r
    val pids = (proposals.map {
      _("id") match {
        case Proposing(who, _) =>
          who
      }
    }).toSet.toSeq

    val stale = Store { s =>
      val tenMinutesAgo = System.currentTimeMillis - (60 * 1000 * 10)
      def stale(key: String) = s.hmget[String, String](key, "mtime") flatMap {
        _.get("mtime").map(_.toLong < tenMinutesAgo)
      }
      def refresh(key: String) = s.exists(key) && stale(key).getOrElse(true)
      pids.filter( p => refresh(Nyc.mukey(p)))
    }
    val members: Map[String, Map[String, String]] = (if (!stale.isEmpty) {
      val cached = pids.filterNot(stale.contains)
      val ms = Meetup.members(stale)
      Store { s =>
        ms.map { m =>
          val data = Map(
            "mu_name" -> m.name,
            "mu_photo" -> m.photo,
            "mtime" -> System.currentTimeMillis.toString
          ) ++ m.twttr.map("twttr" -> _)
          s.hmset(Nyc.mukey(m.id), data)
          m.id -> data
        } ++ cached.map { p =>
          p -> s.hmget[String, String](
            Nyc.mukey(p),
            "mu_name",
            "mu_photo",
            "twttr").get
        }
      }
    } else {
      Store { s =>
        pids.map { p =>
          p -> s.hmget[String, String](
            Nyc.mukey(p),
            "mu_name",
            "mu_photo",
            "twttr").get
        }
      }
    }).toMap

    (proposals /: members)((a, e) => e match {
      case (key, value) =>          
        val (matching, notmatching) = a.partition(_("id").matches(s"""nyc2014:proposals:$key:(.*)"""))
        matching.map(_ ++ value) ++ notmatching
    })
  }

  val viewing: Cycle.Intent[Any, Any] = {
    case req @ GET(Path(Seg("2014" :: "talks" :: Nil))) => Clock("fetching 2012 talks proposals") {
      val (authed, canVote, votes) =  req match {
        case AuthorizedToken(t)
          if (Meetup.has_rsvp(Meetup.Nyc2014.dayoneEventId, t.token)) =>
            val mid = t.memberId.get
            (true, false/*no one can vote right now*/, Store {
              _.smembers(s"nyc2014:talk_votes:$mid")
               .map(_.filter(_.isDefined).map(_.get).toSeq)
               .getOrElse(Nil)
            })
        case AuthorizedToken(t) =>
          (true, false, Nil)
        case _ =>
          (false, false, Nil)
      }
      talkListing(authed,
                  scala.util.Random.shuffle(currentProposals),
                  canVote = false,
                  votes = Nil)
    }
  }

  val creating: Cycle.Intent[Any, Any] = {
    case POST(Path(Seg("2014" :: "proposals" :: Nil))) &
      AuthorizedToken(t) & Params(p) => Clock("creating nyc2014 talk proposal") {
      val expected = for {
        name <- lookup("name") is required("name is required")
        desc <- lookup("desc") is required("desc is required")
        kind <- lookup("kind") is required("kind is required")
      } yield {
        create(t.memberId.get, name.get, desc.get, kind.get)
          .fold({fail =>
            println(s"create request failed $fail")
            JsonContent ~> ResponseString(errorJson(fail))
           },{ ok =>
             println(s"${kind.get} proposal created for ${t.memberId.get}")
             JsonContent ~> ResponseString("""{"status":200,"proposals":%s, "id":"%s"}""" format(ok._1, ok._2))
          })
      }
      expected(p) orFail { errors =>
        println(s"create request validation failed with errors: $errors")
        JsonContent ~> ResponseString(errorJson(
          errors.map { _.error } mkString(". ")
        ))
      }
    }
  } 

  val editing: Cycle.Intent[Any, Any] = {
    case POST(Path(Seg("2014" :: "proposals" :: UrlDecoded(id) :: Nil))) & Params(p) &
      AuthorizedToken(t) => Clock("editing nyc2014 proposal %s" format id) {
      val mid = t.memberId.get
      val expected = for {
        name <- lookup("name") is required("name is required")
        desc <- lookup("desc") is required("desc is required")
        kind <- lookup("kind") is required("kind is required")
      } yield {
        val (n, d, k) = (name.get.trim, desc.get.trim, kind.get)
        (if (n.size > MaxTalkName || d.size > MaxTalkDesc) Left("Talk contents were too long")
        else {
          val Pkey = """nyc2014:proposals:(.*):(.*)""".r
          id match {
            case Pkey(who, pid) =>
              if(!who.equals(mid)) Left("Invalid id")
              else {
                Store { s =>
                  if(!s.exists(id)) Left("Invalid proposal %s" format id)
                  else {
                    s.hset(id, "name", n)
                    s.hset(id, "desc", d)
                    s.hset(id, "kind", k)
                    Right(id)
                  }
                }
              }
           case invalid => Left("Invalid id")
          }
        }) fold({ fail =>
          JsonContent ~> ResponseString(errorJson(fail))
        }, { ok =>
          JsonContent ~> ResponseString("""{"status":200, "id":"%s"}""" format ok)
        })
      }
      expected(p) orFail { errors =>
        JsonContent ~> ResponseString(errorJson(
          errors.map { _.error } mkString(". ")
        ))
      }
    }
  }
}
