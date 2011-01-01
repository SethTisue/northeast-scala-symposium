package com.meetup

trait Cached {
  import com.google.appengine.api.memcache.{Expiration, MemcacheServiceFactory,
                                            MemcacheService}

  case class Cache(val svc: MemcacheService) {
    def isDefinedAt(k: String) = svc.contains(k)
    /** conditional get. */
    def getOr[T](k: String)(f: => (T, Option[Long])): T =
      (svc.get(k) match {
        case null =>
          val (value, expires) = f;
          expires match {
            case None => svc.put(k, value)
            case Some(exp) => svc.put(k, value, Expiration.onDate(new java.util.Date(exp)))
          }
          value
        case value => value
      }).asInstanceOf[T]

    def - (k: String) = svc.delete(k)
  }

  def cache(name: String) = Cache(MemcacheServiceFactory.getMemcacheService(name))
}

trait JsonCached extends Cached {
  import net.liftweb.json.JsonAST._
  import net.liftweb.json.JsonDSL._
  import net.liftweb.json.JsonParser._
  def cacheOr(cname: String, key: String)(fn: => (JValue, Option[Long])) = {
    import net.liftweb.json.JsonParser._
    parse(cache(cname).getOr(key) {
      val (response, expires) = fn
      (compact(render(response)), expires)
    })
  }
}
