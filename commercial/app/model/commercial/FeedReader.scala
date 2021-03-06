package model.commercial

import com.ning.http.client.{Response => AHCResponse}
import common.{ExecutionContexts, Logging}
import conf.Switch
import model.diagnostics.CloudWatch
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}
import scala.util.Try
import scala.xml.{Elem, XML}

object FeedReader extends ExecutionContexts with Logging {

  def read[T](request: FeedRequest)(parse: String => T): Future[Option[T]] = {

    def readUrl(url: String): Future[Option[T]] = {

      def recordLoad(duration: Long):Unit= {
        val feedName = request.feedName.toLowerCase.replaceAll("\\s+", "-")
        val key = s"$feedName-feed-load-time"
        CloudWatch.put("Commercial", Map(s"$key" -> duration.toDouble))
      }

      def parseBody(url: String, body: String): Option[T] = {
        Try(parse(body)).map(Some(_)).recover {
          case e: Exception =>
            log.error(s"Parsing ${request.feedName} feed from $url failed: ${e.getMessage}")
            None
        }.get
      }

      val start = System.currentTimeMillis
      val futureResponse = WS.url(url)
        .withRequestTimeout(request.timeout.toMillis.toInt)
        .get()

      futureResponse map { response =>
        response.status match {
          case 200 =>
            recordLoad(System.currentTimeMillis - start)
            val body = request.responseEncoding map {
              response.underlying[AHCResponse].getResponseBody
            } getOrElse response.body
            parseBody(url, body)
          case other =>
            recordLoad(-1)
            val feedName = request.feedName
            val statusText = response.statusText
            log.error(s"Reading $feedName feed from $url failed: $other: $statusText")
            None
        }
      } recover {
        case e: Exception =>
          recordLoad(-1)
          log.error(s"Reading ${request.feedName} feed from $url failed: ${e.getMessage}")
          None
      }
    }

     request.switch.onInitialized flatMap { switch =>
       if (switch.isSwitchedOn) {
        request.url map readUrl getOrElse {
          log.warn(s"Missing URL for ${request.feedName} feed")
          Future.successful(None)
        }
      } else {
        log.warn(s"Reading ${request.feedName} feed failed: Switch is off")
        Future.successful(None)
      }
    }

  }

  def readSeq[T](request: FeedRequest)(parse: String => Seq[T]): Future[Seq[T]] = {
    read(request)(parse) map {
      case Some(items) =>
        log.info(s"Loaded ${items.size} ${request.feedName} from ${request.url.get}")
        items
      case None =>
        log.warn(s"Empty ${request.feedName} feed")
        Nil
    }
  }

  def readSeqFromXml[T](request: FeedRequest)(parse: Elem => Seq[T]): Future[Seq[T]] = {
    readSeq(request) { body =>
      parse(XML.loadString(body))
    }
  }

  def readSeqFromJson[T](request: FeedRequest)(parse: JsValue => Seq[T]): Future[Seq[T]] = {
    readSeq(request) { body =>
      parse(Json.parse(body))
    }
  }

}


case class FeedRequest(feedName: String, switch: Switch, url: Option[String], timeout: Duration = 2.seconds, responseEncoding: Option[String] = None)
