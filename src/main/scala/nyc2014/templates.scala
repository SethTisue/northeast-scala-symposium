package nescala.nyc2014

import java.text.SimpleDateFormat
import java.util.Date
import nescala.Meetup
import java.net.URLEncoder.encode

trait Templates {

  val meetupGroup = "http://www.meetup.com/nescala/"
  val dayoneLink = s"${meetupGroup}events/${Meetup.Nyc2014.dayoneEventId}/"
  val daytwoLink = s"${meetupGroup}events/${Meetup.Nyc2014.daytwoEventId}/"

  def btn(link: String, display: String) = <a href={link} class="button">{display}</a>

  def indexPage
    (authed: Boolean,
     sched: Option[Schedule] = None,
     proposals: Seq[Proposal] = Nil) =
    layout(Nil)(
    <script type="text/javascript" src="/js/jquery.scrollTo.min.js"></script>
    <script type="text/javascript" src="/js/nyc2014/nyc.js"></script>
    <script type="text/javascript" src="/js/nyc2014/index.js"></script>)(
      head ++ blurb(authed) ++ schedule(authed, sched, proposals) ++ where ++ kindness
    )

  def talkListing
    (authed: Boolean,
     proposals: Seq[Proposal],
     canVote: Boolean = false,
     votes: Seq[String] = Seq.empty[String]) = layout(
    <script type="text/javascript" src="/js/nyc2014/voting.js"></script>)(Nil)({
      head
    } ++ (<section>
      <div class="grid">
        <div class="unit whole center">
          <h2>
            { proposals.size } Scala campfire { if (proposals.size == 1) "story" else "stories" } submitted
          </h2>
         </div>
         <div class="unit whole center lead">
          <p>
            This year's symposium features talks in three flavors:
          </p>
          <div class="grid">
            <div class="unit one-third">
              <a href="#medium-proposals">Med</a> (45 min)
              <p class="small mute">3 slots</p>
            </div>
            <div class="unit one-third">
              <a href="#short-proposals">Short</a> (30 min)
              <p class="small mute">4 slots</p>
            </div>
            <div class="unit one-third">
              <a href="#lightning-proposals">Lightning</a> (15 min)
              <p class="small mute">6 slots</p>
            </div>
          </div>
        </div>
        <div class="unit whole center" id="proposed">
          <p>
          The deadline for voting on your favorite talk is now over. Results will be announced soon.
          </p>
        </div>
      </div>
    </section>
    <section>
     <div class="grid">{
        proposals.groupBy(_.kind).map {
          case (kind, ps) =>
            <p id={s"$kind-proposals"} class="unit whole">{ps.size} <strong>{ kind }</strong> length proposals</p>
            <ul>{ ps.map(proposal(canVote, votes)) }</ul>
        }
     }
    </div>
   </section>)
  )

  def links(proposal: Proposal) =
    (<div class="links">
     <a class="primary" href={ s"http://meetup.com/nescala/members/${proposal.memberId}" } target="_blank">
       { proposal.member.get.name }
     </a>
      {
        if (proposal.member.get.twttr.isDefined) (
          <p>
            <i class="fa fa-twitter"></i>
            <a class="twttr small" href={ s"http://twitter.com/${proposal.member.get.twttr.get.drop(1)}"} target="_blank">
             { proposal.member.get.twttr.get }
            </a>
          </p>
        )
      }
    </div>)

  def avatar(p: Proposal) =
    <a href={ s"http://meetup.com/nescala/members/${p.memberId}"}
      class="circle" style={s"background-image:url(${p.member.get.thumbPhoto}); background-size: cover; background-position: 50%"}>
    </a>

  def personal(p: Proposal): xml.NodeSeq = avatar(p) ++ links(p)

  def proposal(canVote: Boolean, votes: Seq[String])(p: Proposal)  =
   <li class="unit whole talk" id={ p.domId }>
    <div class="grid">
      <div class="unit one-fifth">
        { personal(p) }
      </div>
      <div class="unit four-fifths">
        <h2><a href={ "#"+p.domId }>{ p.name }</a></h2>
        <div>
          <span class="mute">{ p.kind match {
            case "medium" => "45 minutes"
            case "short" => "30 minutes"
            case "lightning" => "15 minutes"
          } }
          </span>
          { if (canVote && votes.contains(p.id))
            <form class="ballot" action="/2014/votes" method="POST">
             <input type="hidden" name="vote" value={ p.id }/>
             <input type="hidden" name="action" value={ if (votes.contains(p.id)) "unvote" else "vote" }/>
             <input type="submit" class="voting btn"
              value="This got your vote" disabled="disabled"/>
            </form>
          }
        </div>          
        <p class="desc">{ p.desc }</p>
      </div>
    </div>
    <hr/>
  </li>

  def proposing(authed: Boolean, proposals: Seq[Proposal] = Nil): xml.NodeSeq =
    if (authed) propose(proposals) else <section>
      <div class="grid" id="propose">
        <div class="unit whole">
          <h2>Speak up</h2>
          <p>
           The deadline for submitting <a href="/2014/talks">talk proposals</a> is now passed.
          </p>
          <p>
           Attendees of <a href={dayoneLink}>Day 1</a> may still <a href="/login?then=vote" class="btn">Login to vote</a>
          </p>
          <p>
            Remember, selected speakers are guaranteed a spot on the RSVP list, <strong>and</strong> a spot for a friend or colleague.
          </p>
        </div>
      </div>
    </section>

  def formatTime(d: Date) =
    (if (d.getMinutes > 0) new SimpleDateFormat("h:mm")
     else new SimpleDateFormat("h")).format(d)

  def ampm(d: Date) =
    new SimpleDateFormat("aa").format(d).toLowerCase

  def scheduleItem(slot: Slot): xml.NodeSeq = slot match {
    case NonPresentation(time, title, content) =>
      <div class="unit whole slot">
        <div class="grid">
          <h3 class="unit one-fifth">
            <span class="time">{ formatTime(time) }</span>
            <span class="ampm">{ ampm(time) }</span>
          </h3>
          <h3 class="unit four-fifths">{ title }</h3>
        </div>
        <div class="grid">
          <p class="unit one-fifth"></p>
          <p class="unit four-fifths">{ content }</p>
        </div>
      </div>
    case p @ Presentation(proposal) =>
      <div class="unit whole slot" id={ s"talk-${proposal.domId}" }>
        <div class="grid">
          <h3 class="unit one-fifth">
            <span class="time">{ formatTime(p.time) }</span>
            <span class="ampm">{ ampm(p.time) }</span>
          </h3>
          <h3 class="unit four-fifths">
            <a href={s"#talk-${proposal.domId}"}>{ p.title }</a>
          </h3>
        </div>
        <div class="grid">
          <div class="unit one-fifth">
            <p>{ personal(proposal) }</p>
          </div>
          <div class="unit four-fifths">
            <p>{ p.content }</p>
          </div>
        </div>
      </div>
  }

  def schedule(
    authed: Boolean,
    sched: Option[Schedule] = None,
    proposals: Seq[Proposal]): xml.NodeSeq =
    (<section id="schedule">
      <div class="grid">
        <div class="unit whole">
          <h2>Schedule</h2>
        </div>
        <div class="unit whole">
          <h3>Saturday / 2nd</h3>
        </div>{
          sched.map(_.slots.map(scheduleItem))
                .getOrElse(<span></span>)
       }</div>
    </section>)
    
  def newProposalsLeft(proposals: Seq[Proposal]) = 
   (<div class="unit one-third">{ if (proposals.size < Proposals.MaxProposals)
     <p class="instruct">
       Please provide a brief single-paragraph description of your proposed talk.
     </p>
     <p class="instruct">
       Selected speakers may enter <a href="http://www.meetup.com/account/services/">Twitter usernames</a> and other biographical
       information on their
       <a target="_blank" href="http://www.meetup.com/account/">
         Meetup member profile
            </a>.
          </p>
          <p class="instruct">
            Each attendee can post at most <strong>three</strong> talk proposals.
          </p>
        else
          <p class="instruct">You have used up all your talk proposals</p>
       }</div>)

  def newProposalForm =
    <form action="/2014/proposals" method="POST" id="propose-form" class="proposing">
      <div id="proposal-notifications">
        <div id="proposal-notification"></div>
        <div id="proposal-edit-notification"></div>
      </div>
      <div class="grid">
        <div class="unit half">
          <label for="kind">How long do you intend to woo us?</label>
        </div>
        <div class="unit half">
          <select name="kind">
            <option value="medium">Medium (45 min)</option>
            <option value="short">Short (30 min)</option>
            <option value="lightning">Lightning (15 min)</option>
          </select>
        </div>
      </div>
      <div class="grid">
        <div class="unit whole">
          <label for="name">What's your talk called?</label>
        </div>
        <div class="unit whole">
          <input type="text" name="name" maxlength={ Proposals.MaxTalkName + "" } placeholder="How I learned to love my type system" />
        </div>
      </div>
      <div class="grid">
        <div class="unit whole">
          <label for="desc">What's your talk is about?</label>
        </div>
        <div class="unit whole">
          <div class="limited">
            <textarea name="desc" data-limit={ Proposals.MaxTalkDesc + "" }
              placeholder={ "Say it in %s characters or less" format(
                  Proposals.MaxTalkDesc) }></textarea>
          </div>
          <div class="limit-label"></div>
        </div>
        <input type="submit" value="Propose Talk" class="btn" />
      </div>
    </form>

  def propose(proposals: Seq[Proposal]): xml.NodeSeq = (<section>
    <div class="grid" id="propose">
      <div class="unit whole">
        <h2>Speak up</h2>
        <p>The deadline for submitting <strong>new</strong> <a href="/2014/talks">talk proposals</a> has now passed.</p>{
          if (proposals.isEmpty) <p>Attendees may still get their voice heard. Attendees select the speakers by <a href="/2014/talks">voting</a>. Selected speakers are guaranteed a spot on the RSVP list.</p> else <p>Selected speakers are guaranteed a spot on the RSVP list, <strong>and</strong> a spot for a friend or colleague.</p>
        }
      </div>
      <div id="propose-talk">
        <div id="propose-container" class="unit two-thirds">
          { if (proposals.nonEmpty) <p>You may make changes to your proposals below</p> ++ proposalList(proposals) else <span></span> }
        </div>
      </div>
    </div>
  </section>)

  def proposalList(props: Seq[Proposal]) =
    listOf(props, "proposals", Proposals.MaxTalkName,
           Proposals.MaxTalkDesc, "Your talk proposals", "Edit Talk", "#propose-form")

  def listOf(
    props: Seq[Proposal],
    kind: String,
    maxName: Int,
    maxDesc: Int,
    listTitle: String,
    editLabel: String,
    sourceForm: String) =
    <div id={ kind }>
      <h2 class="proposal-header">{ listTitle }</h2>
       <ul id={ "%s-list" format kind }>{ if (props.isEmpty) { <li id="no-proposals" class="instruct">None yet</li>} }
       {
         props.map { p =>
         <li id={ p.id }>
           <form action={ s"/2014/$kind/${encode(p.id, "utf8")}" }
                 method="POST" class="propose-edit-form">
             <div>
               <h3>
                 <a href="#" class="toggle name" data-val={ p.name }>{ p.name }</a>
               </h3>
               <input type="text" name="name" maxlength={ maxName + "" } value={ p.name } />
             </div>
             <div class="preview">
               <div class="controls clearfix">
                 <a href="#" class="edit-proposal" data-proposal={ p.id }>
                    Make some quick changes
                  </a> or <a href={"mailto:doug@meetup.com?subject=please withdraw talk %s" format p.id }>
                    Email us
                  </a> if you wish to withdraw this talk.
               </div>
               <div>
                 <p>
                    This is a <select class="edit-kind" name="kind" disabled="disabled">
                    <option value="medium" selected={ Option(p.kind).filter(_ == "medium").map( _ => "selected").orNull }>Medium (45 min)</option>
                    <option value="short" selected={ Option(p.kind).filter(_ == "short").map( _ => "selected").orNull }>Short (30 min)</option>
                    <option value="lightning" selected={ Option(p.kind).filter(_ == "lightning").map( _ => "selected").orNull }>Lightning (15 min)</option>
                    </select> length talk.
                 </p>
               </div>
               <p class="linkify desc" data-val={ p.desc }>{ p.desc }</p>
               <div class="edit-desc limited">
                  <textarea data-limit={ maxDesc + "" } name="desc">{ p.desc }</textarea>
                  <div class="limit-label"></div>
                  <div class="form-extras">
                    <div class="edit-controls clearfix">
                      <input type="submit" value={ editLabel } class="btn" />
                      <input type="button" value="Cancel" class="btn cancel" />
                    </div>
                  </div>
                </div>
             </div>
           </form>
         </li>
         }
       }
     </ul>
    </div>

  def layout(head: xml.NodeSeq)
    (bodyScripts: xml.NodeSeq)
    (body: xml.NodeSeq) = unfiltered.response.Html5(
      <html>
      <head>
        <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>&#8663;northeast scala symposium</title>
        <link href="http://fonts.googleapis.com/css?family=Source+Code+Pro|Montserrat:400,700|Open+Sans:300italic,400italic,700italic,400,300,700" rel="stylesheet" type="text/css"/>
        <link rel="stylesheet" type="text/css" href="/css/gridism.css" />
        <link rel="stylesheet" type="text/css" href="/css/normalize.css" />
        <link rel="stylesheet" type="text/css" href="/css/nyc2014.css" />
        <link href="//netdna.bootstrapcdn.com/font-awesome/4.0.3/css/font-awesome.css" rel="stylesheet"/>
        <script type="text/javascript" src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
        { head }
      </head>
      <body class="wrap wider" id="top">
        { body }
        <footer>
          <div class="grid">
            <div class="unit whole">
              <a href="#top">nescala</a> is made possible with <span class="love">&#10084;</span> from the
              <div>
                <a href="http://www.meetup.com/boston-scala/">Boston</a>,
                <a href="http://www.meetup.com/scala-phase/">Philadelphia</a>,
                and <a href="http://www.meetup.com/ny-scala/">New York</a> scala enthusiasts and, of course, of all of
                <a href="http://www.meetup.com/nescala/photos/">you</a>.
              </div>
            </div>
            <div class="unit whole">
              { twttrFollow }
            </div>
            <div class="unit whole">
              hosting by <a href="http://www.heroku.com/">heroku</a>
            </div>
            <div class="center">
              <div class="unit whole">
                <p>
                  This year's symposium, uses structural sharing with those, 3 years passed.
                </p>
              </div>
              <p class="unit one-third">
                <a href="/2013">2013</a>
              </p>
              <p class="unit one-third">
                <a href="/2012">2012</a>
              </p>
              <p class="unit one-third">
                <a href="/2011">2011</a>
              </p>
            </div>
          </div>
        </footer>
        <script type="text/javascript" src="/js/nyc2014/nyc.js"></script>
        { bodyScripts }
      </body>
    </html>
  )

  val twttrFollow = {
    <a href="https://twitter.com/nescalas" class="twitter-follow-button" data-show-count="false" data-size="large">Follow @nescalas</a>
  }

  def head =
   <header>
    <div class="grid">
      <div class="unit whole center title">
        <a href="/" class="logo">
          <img class="two-thirds" src="/images/2014-logo.svg" alt="north east scala symposium logo"></img>
        </a>
        <div class="center">
          <hr/>
          <h4><a href="#where">New York, NY</a></h4>
          <h4>March 1 <span class="amp">&amp;</span> 2, 2014</h4>
        </div>
        </div>
      </div>
    </header>

  def blurb(authed: Boolean) = (<section>
    <div class="grid">
      <div class="unit whole lead center">
        <p>
          A gathering of statically refined types
        </p>
        <p class="amp">&amp;</p>
        <p>
          well-spoken peers
        </p>
        <hr/>
        <p class="small mute">
         self-selected by you, from the northeasterly regions of the US
        </p>
      </div></div></section><section><div class="grid">
      <div class="unit half">
        <h2>One day of <strong>sharing</strong>.</h2>
        <p class="mute">Sat Mar 1, 8am to 6pm</p>
        <p>
         <a href={dayoneLink}>Day 1</a> is back to <strong>basics</strong> with <a href="#whereone">one room</a>, <a href="/#schedule">one track of talks</a>.
        </p>
        <p>
          Seating on day 1 is <strong>limited</strong> and is now sold out. (Join the <a href={dayoneLink}>day 1</a> waiting list to be notified when a spot opens if someone cancels.)
        </p>
      </div>
      <div class="unit half">
        <h2>One day of <strong>pairing</strong>.</h2>
        <p class="mute">Sun Mar 2, 9am to 5pm</p>
        <p>
          <a href={daytwoLink}>Day 2</a> is a free-to-attend, hands-on <a href="http://en.wikipedia.org/wiki/Unconference">unconference</a> 
          self-organized on the spot by whoever shows up hosted @ <a href="#wheretwo">Meetup HQ</a>.
        </p>
        <p>
          In past years we've had workshops, panels, talks that didn't make it into day 1, debates, group hugs (well, not really).
          The unconference is whatever you want to make of it.
        </p>
        <p>
          We're taking over all three floors of Meetup HQ, so there will be plenty of room for everyone,
          even those who didn't get in for <a href={dayoneLink}>Day 1</a>.
        </p>
        <p>
          Unconference participants will collectively fill the schedule grid first thing Sunday morning.
          Bring your laptop, and your conversation ideas.
        </p>
       <p>
         Rooms, projectors, whiteboards, and markers will be provided. (Video adapters will not be provided! Make sure you bring your own.)
       </p>
       <p>
         There might be food; we're working on it.
       </p>
      </div>
    </div>
  </section>)

  val where = <section>
    <div class="grid">
      <div class="unit whole" id="where">
        <h2>Come. Find us.</h2>
        <p id="whereone">
          This year's talk symposium will be held @ <a target="_blank" href="http://www.cims.nyu.edu/">Courant Institute of Mathematical Sciences</a>.
        </p>
        <div id="whereone-iframe"></div>
        <p id="wheretwo">
          Day one will be followed by an unconference hosted @ <a target="_blank" href="http://meetup.com">Meetup</a> HQ on <a href={daytwoLink}>day two</a>.
        </p>
        <div id="wheretwo-iframe"></div>
      </div>
    </div>
  </section>

  val kindness = <section id="kindness">
    <div class="grid">
      <div class="unit whole">
      <h2>Be kind.</h2>
      <p>
        Nobody likes a jerk, so show respect for those around you.
      </p>
      <p>
       NE Scala is dedicated to providing a harassment-free conference experience for everyone, regardless of gender, gender identity and expression, sexual orientation, disability, physical appearance, body size, race, or religion (or lack thereof). We do not tolerate harassment of conference participants in any form.
      </p>
      <p>
        All communication should be appropriate for a professional audience including people of many different backgrounds. Sexual language, innuendo, and imagery is not appropriate for any conference venue, including talks.
      </p>
      <p>
        Conference participants violating these rules may be asked to leave the conference without a refund at the sole discretion of the conference organizers.
      </p>
      </div>
    </div>
  </section>

  val attending = <section>
    <div class="grid">
      <div class="unit whole day" data-event={ Meetup.Nyc2014.dayoneEventId }>
        <ul class="rsvps"></ul>
      </div>
    </div>
  </section>


  def talliedKind(total: Int)(el: (String, Seq[Proposal])): xml.NodeSeq = {
    val (kind, entries) = el
    <div class="unit whole">
      <h2>{ s"${entries.map(_.votes).sum} $kind" } votes</h2>
        <ul data-total={ total.toString } id="tallies">{ entries map { e =>
          <li class="clearfix" title={ e.member.get.name } id={ s"e-${e.id}" }
              data-score={ ((e.votes.toDouble / total) * 100).toString }>
           <p>
             <a class="circle" style={s"background-image:url(${e.member.get.thumbPhoto}); background-size: cover; background-position: 50%"}
                href={ s"/2014/talks#${e.domId}" }>
             </a>
             <strong>{ e.votes }</strong> { e.name }
           </p>
          </li>
       } }</ul>
    </div>
  }

  def tallied(authed: Boolean, total: Int, entries: Map[String, Seq[Proposal]]) =
    (layout(Nil)(Nil)
      ({ head } ++ <div class="grid">{
        <div class="unit whole">
          <p><strong>{ total }</strong> votes submitted so far</p>
        </div>++ {
           entries.map(talliedKind(total))
         }
       }</div>))
}

object Templates extends Templates {}

