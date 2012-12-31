package nescala

object NESS extends Config {
  import unfiltered.Cycle.Intent
  import unfiltered.request._
  import unfiltered.request.QParams._
  import unfiltered.response._
  import unfiltered.Cookie

  import dispatch.meetup._
  import dispatch.oauth.Token

  def site: Intent[Any, Any] = {
    
    case GET(Path(Seg("login" :: Nil))) & Params(p) =>
      val callbackbase = "%s/authenticated" format property("host")
      val callback = p("then") match {
        case Seq(then) => "%s?then=%s" format(callbackbase, then)
        case _ => callbackbase
      }
      val t = http(Auth.request_token(Meetup.consumer, callback))
      // drop cookie and redirect to meetup for auth
      SetCookies(
        Cookie("token",
               ClientToken(t.value, t.secret, Hashing(t.value, t.secret)).toCookieString,
               httpOnly = true)) ~>
          Redirect(Auth.authenticate_url(t).to_uri.toString)

    case GET(Path(Seg("logout" :: Nil))) =>
      SetCookies.discarding("token") ~> Redirect("/")

    case req @ GET(Path(Seg("authenticated" :: Nil))) & Params(params) =>
      val expected = for {
        verifier <- lookup("oauth_verifier") is
          required("verifier is required") is
          nonempty("verifier can not be blank")
        token <- lookup("oauth_token") is
          required("token is required") is
          nonempty("token can not be blank")
        then <- lookup("then") is optional[String, String]
      } yield {
        CookieToken(req).map { rt =>
            val at = http(Auth.access_token(Meetup.consumer, Token(rt.value, rt.sec), verifier.get))
            val mid = Some(Meetup.member_id(at).toString)
            SetCookies(
                Cookie("token",
                       ClientToken(at.value,
                                   at.secret,
                                   Hashing(at.value, at.secret, verifier.get, mid.get),
                                   verifier,
                                   mid)
                         .toCookieString,
                       httpOnly = true)) ~>  Redirect(then.get match {
                  case Some("vote") => "/2013/talks#proposed"
                  case Some("talk") => "/#propose-talk"
                  case Some("proposals") => "/2013/talk_tally"
                  case Some("panel_proposals") => "/2013/panel_tally"
                  case other => "/"
                })
        }.getOrElse(sys.error("could not find request token"))
      }

      expected(params) orFail { errors =>
        BadRequest ~> ResponseString(errors.map { _.error } mkString(". "))
      }
  }

  def http = dispatch.Http
}
