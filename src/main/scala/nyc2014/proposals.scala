package nescala.nyc2014

import nescala.{ AuthorizedToken, Cached, Clock, Meetup, Store }
import nescala.request.UrlDecoded
import unfiltered._
import unfiltered.request._
import unfiltered.request.QParams._
import unfiltered.response._
import java.util.Date

object Proposal {
  def fromMap(data: Map[String, String]) =
    Proposal(data("id"),
             data("name"),
             // we don't always fetch the desc
             data.getOrElse("desc", ""),
             data("kind"),
             data.getOrElse("votes", "0").toInt)
}

case class Proposal(
  id: String,
  name: String,
  desc: String,
  kind: String,
  votes: Int = 0,
  member: Option[Member] = None) {
  lazy val domId = id.split(":")(3)
  lazy val memberId = id.split(":")(2)
}

object Talk {
  def fromMap(data: Map[String, String]) =
    Talk(data("id"),
         data("name"),
         // we don't always fetch the desc
         data.getOrElse("desc", ""),
         data("kind"),
         data.get("slot").map(s => new Date(s.toLong)).getOrElse(new Date))
}

case class Talk(
  id: String,
  name: String,
  desc: String,
  kind: String,
  slot: Date,
  member: Option[Member] = None
)

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
      if (!s.exists(key)) Left("proposal did not exist") else {
        key match {
          case Withdrawing(who, id) =>
            if (mid.equals(who)) {
              s.del(key).map( stat => if(stat > 0) s.decr(
                s"count:nyc2014:proposals:$who"
              ))
              Right(key)
            } else Left("not authorized to withdraw this proposal")
          case bk =>
            Left("invalid proposal")
        }
      }
   })
  }

  // decommissioned
  private def create(mid: String, name: String, desc: String, kind: String) = {
    val (n, d) = (name.trim, desc.trim)
    Store { s =>
      if (n.size > MaxTalkName || d.size > MaxTalkDesc) Left("Talk contents were too long")
      else {
        // `key` notes :)
        // talks are persisted as keys and values
        // count:{city}:proposals:{memberId} stores the number of proposals a member submitted
        // {city}:proposals:ids stores an atomicly incremented int used to generate proposals ids
        // {city}:proposals:{memberId}:{nextId} stores a map of name, desc, and votes for a proposal
        val mkey = s"nyc2014:proposals:$mid"
        val mukey = s"nyc2014:members:$mid"
        val ckey = s"count:$mkey"
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

  def promote(proposal: String, slot: Date) =
    Store { s =>
      val Promoting = """nyc2014:proposals:(.*):(.*)""".r
      if (!s.exists(proposal)) Left(s"proposal $proposal does not exist")
      else proposal match {
        case Promoting(who, id) =>
          val talkKey = s"nyc2014:talks:$who:$id"
          if (s.exists(talkKey)) Left(s"already promoted to $talkKey") else {
            val prop = s.hmget(proposal, "name", "desc", "kind")
              .map(_ + ("id" -> proposal)).map {
                Proposal.fromMap(_)
              }
            prop.map { p =>
              s.hmset(talkKey, Map(
                "name" -> p.name,
                "desc" -> p.desc,
                "kind" -> p.kind,
                "slot" -> slot.getTime.toString
              ))
            }.getOrElse(Left(s"failed to resolve proposal info for $proposal"))
          }
        case invalid => Left(s"invalid proposal $invalid")
      }
    }

  def demote(talk: String) = 
    Store { s =>
      val Talking = """nyc2014:talks:(.*):(.*)""".r
      if (!s.exists(talk)) Left(s"talk $talk does not exist") else talk match {
        case Talking(_, _) => Right(s.del(talk))
        case _ => Left(s"$talk is not a valid key")
      }
    }

  def currentProposals = {
    val proposals = Store { s =>
      s.keys("nyc2014:proposals:*:*") match {
        case None => Seq.empty[Proposal]
        case Some(keys) =>
          (List.empty[Proposal] /: keys.filter(_.isDefined)){
            (a, e) =>
              s.hmget[String, String](
                e.get, "name", "desc", "kind")
                .map(_ + ("id" -> e.get)).map {
                   Proposal.fromMap(_) :: a
                }.getOrElse(a)
          }
      }
    }

    val Proposing = """nyc2014:proposals:(.*):(.*)""".r
    val pids = (proposals.map {
      _.id match {
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
    val members: Map[String, Member] = (if (!stale.isEmpty) {
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
          m.id -> Member.fromMap(m.id, data)
        } ++ cached.map { p =>
          val data = s.hmget[String, String](
            Nyc.mukey(p), "mu_name", "mu_photo", "twttr").get
           p -> Member.fromMap(p, data)
        }
      }
    } else {
      Store { s =>
        pids.map { p =>          
          val data = s.hmget[String, String](
            Nyc.mukey(p),
            "mu_name",
            "mu_photo",
            "mtime",
            "twttr").get
          p -> Member.fromMap(p, data)
        }
      }
    }).toMap

    (proposals /: members) {
      (a, e) => e match {
        case (memberId, member) =>          
          val (matching, notmatching) =
            a.partition(_.id.matches(s"""nyc2014:proposals:$memberId:(.*)"""))
          matching.map(_.copy(member = Some(member))) ++ notmatching
      }
    }
  }

  val viewing: Cycle.Intent[Any, Any] = {
    case req @ GET(Path(Seg("2014" :: "talks" :: Nil))) => Clock("fetching 2012 talks proposals") {
      val (authed, canVote, votes) =  req match {
        case AuthorizedToken(t)
          if (Meetup.has_rsvp(Meetup.Nyc2014.dayoneEventId, t.token)) =>
            println("has rsvp and logged in")
            val mid = t.memberId.get
            val votekey = s"nyc2014:talk_votes:$mid"
            println(s"fetching votes for $votekey")
            val currentVotes = Store {
              _.smembers(votekey)
               .map(_.filter(_.isDefined).map(_.get).toSeq)
               .getOrElse(Nil)
            }
            println(s"current votes $currentVotes")
            (true, true, currentVotes)
        case AuthorizedToken(t) =>
          println("logged in without rsvp")
          (true, false, Nil)
        case _ =>
          println("alien")
          (false, false, Nil)
      }
      talkListing(authed,
                  scala.util.Random.shuffle(currentProposals),
                  canVote = canVote,
                  votes   = votes)
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
