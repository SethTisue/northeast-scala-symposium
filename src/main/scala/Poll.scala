package nescala

import models.Vote
import scala.util.Random
import unfiltered.request._
import unfiltered.response._
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection.JavaConverters._

object Poll extends Templates with nyc.Entries {
  val VOTES = 10
  def intent: unfiltered.filter.Plan.Intent = {
    //case POST(Params(params) & CookieToken(ClientToken(v, s, Some(c), Some(m)))) =>
    //  JsonContent ~> ResponseString(compact(render(Nil)))

    case GET(AuthorizedToken(t)) =>
      page(
        <div id="countdown"><span id="remaining">... votes</span> left</div> ++ {
        Random.shuffle(entries.zipWithIndex).map { case (entry, index) =>
          <div class="entry" id={"e-%s" format index}>
             <h4>{entry.speaker}</h4>
             <h4><a href="#" class="vote" id={"v" + index}>Vote</a> {entry.title}</h4>
             <p>{entry.description}</p>
          </div>
        }
      })

    case GET(Params(NoRsvp(params))) => page(
      <p>You must <a href="http://www.meetup.com/ny-scala/calendar/15526582">rsvp</a> to vote!</p>
    )

    case GET(_) => page(<p><a href="/connect">Sign in with Meetup</a></p>)

    case _ => BadRequest
  }

  def page = layout(Nil)(
    <script type="text/javascript" src="/js/vote.js"></script>
  )_
}

object NoRsvp extends Params.Extract("norsvp", Params.first)
