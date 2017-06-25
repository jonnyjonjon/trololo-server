import java.net.{InetAddress, UnknownHostException}

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class IpInfo(query: String,
                  status: String,
                  country: Option[String],
                  message: Option[String])

trait Protocols extends DefaultJsonProtocol {
  implicit val ipInfoFormat = jsonFormat4(IpInfo.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  lazy val ipApiConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = Http().outgoingConnection(
    config.getString("services.ip-api.host"),
    config.getInt("services.ip-api.port"))

  def ipApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request)
    .via(ipApiConnectionFlow).runWith(Sink.head)

  def fetchIpInfoMessage(ip: String): Future[String] = {
    ipApiRequest(RequestBuilding.Get(s"/json/$ip")).flatMap { response =>
      response.status match {
        case OK =>
          Unmarshal(response.entity).to[IpInfo].map { ipInfo =>
            (ipInfo.status, ipInfo.country, ipInfo.message) match {
              case ("success", Some(country), None) =>
                s"success for query ${ipInfo.query}: $country"
              case ("fail", None, Some(message)) =>
                s"failure for query ${ipInfo.query}: $message"
              case _ =>
                s"failure for query ${ipInfo.query}: returned invalid ip info $ipInfo"
            }
          }
        case _ =>
          Unmarshal(response.entity).to[String].map { entity =>
            s"failure for query $ip: bad status code ${response.status}, returned entity $entity"
          }
      }
    }
  }

  def isPrivateIp(ip: String): Try[Boolean] = {
    Try(new InetAddress(ip)).map(_.isSiteLocalAddress)
  }

  val routes = {
    logRequestResult("trololo-server") {
      path("xfftest") {
        get {
          (parameters('ip.?) & optionalHeaderValueByName("X-Forwarded-For")) { (maybeIp, maybeXff) =>
            complete {
              val message = maybeXff match {
                case Some(xff) =>
                  val leftMostIp = xff.takeWhile(_ != ',')
                  isPrivateIp(leftMostIp) match {
                    case Success(false) =>
                      s"success: xff header $xff parsed to ip $leftMostIp, which is not private"
                    case Success(true) =>
                      s"failure: xff header $xff parsed to ip $leftMostIp, which is private"
                    case Failure(_) =>
                      s"failure: xff header $xff parsed to ip $leftMostIp, which is an invalid address"
                  }
                case _ =>
                  "failure: no x-forwarded-for header to parse"
              }
              logger.error(message)
              NoContent
            }
          }
        }
      }
    }
  }
}

object TrololoServer extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
