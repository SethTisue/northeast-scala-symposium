package nescala.boston

trait SponsorTemplate { self: Templates =>
  def sponsors =
    bostonLayout(Nil)(
    <script type="text/javascript" src="/js/jquery.scrollTo-1.4.2-min.js"></script>
    <script type="text/javascript" src="/js/boston/index.js"></script>)(
      head(true/*hide login*/) ++ 
      <div id="sponsors">
        <h1>A Little Help From Our Friends</h1>
        <p>If you get more than your money's worth at nescala, it's thanks to these guys.</p>
        <a href="https://foursquare.com/jobs/"><img src="/images/sponsors/foursquare.png" /></a>
        <p>Foursquare is buying your drinks Saturday night.</p>
        <a href="http://www.ardentex.com/software.html"><img src="/images/sponsors/ardentex.png" /></a>
      </div>
    )
}
