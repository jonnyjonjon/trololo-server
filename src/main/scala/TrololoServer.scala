import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContextExecutor

trait Service {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val routes = {
    logRequestResult("trololo-server") {
      path("xfftest") {
        get {
          (parameters('ip.?) & optionalHeaderValueByName("X-Forwarded-For")) { (maybeIp, maybeXff) =>
            complete {
              val ipMessage = maybeIp match {
                case Some(ip) =>
                  s"ip parameter value:$ip, "
                case _ =>
                  s"ip parameter missing! "
              }
              val xffMessage = maybeXff match {
                case Some(xff) =>
                  s"xff header value:$xff"
                case _ =>
                  s"xff header missing!"
              }
              logger.error(ipMessage + xffMessage)
              // always completing with a 204 to trigger passback
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
