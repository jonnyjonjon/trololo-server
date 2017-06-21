import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import scala.concurrent.{ExecutionContextExecutor, Future}
import spray.json.DefaultJsonProtocol

case class TrololoPairRequest(trololo1: String, trololo2: String)
case class TrololoInfo(yayayayaya: String, hahahahaha: String)
case class TrololoSummary(a: TrololoInfo, b: TrololoInfo)

trait Protocols extends DefaultJsonProtocol {
  implicit val TrololoPairRequestFormat = jsonFormat2(TrololoPairRequest.apply)
  implicit val TrololoInfoFormat = jsonFormat2(TrololoInfo.apply)
  implicit val trololoSummaryFormat = jsonFormat2(TrololoSummary.apply)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  lazy val trololoConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("services.trololo.host"), config.getInt("services.trololo.port"))

  def trololoApiRequest(request: HttpRequest): Future[HttpResponse] = Source.single(request).via(trololoConnectionFlow).runWith(Sink.head)

  def fetchTrololoInfo(trololo: String): Future[Either[String, TrololoInfo]] = {
    trololoApiRequest(RequestBuilding.Get(s"/json/$trololo")).flatMap { response =>
      response.status match {
        case OK => Unmarshal(response.entity).to[TrololoInfo].map(Right(_))
        case BadRequest => Future.successful(Left(s"$trololo: incorrect trololo"))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Trololo request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }

  val routes = {
    logRequestResult("trololo-server") {
      pathPrefix("ip") {
        (get & path(Segment)) { trololo =>
          complete {
            fetchTrololoInfo(trololo).map[ToResponseMarshallable] {
              case Right(trololoInfo) => trololoInfo
              case Left(errorMessage) => BadRequest -> errorMessage
            }
          }
        } ~
        (post & entity(as[TrololoPairRequest])) { trololoPairRequest =>
          complete {
            val trololo1InfoFuture = fetchTrololoInfo(trololoPairRequest.trololo1)
            val trololo2InfoFuture = fetchTrololoInfo(trololoPairRequest.trololo2)
            trololo1InfoFuture.zip(trololo2InfoFuture).map[ToResponseMarshallable] {
              case (Right(info1), Right(info2)) => TrololoSummary(info1, info2)
              case (Left(errorMessage), _) => BadRequest -> errorMessage
              case (_, Left(errorMessage)) => BadRequest -> errorMessage
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
