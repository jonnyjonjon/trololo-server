import akka.event.NoLogging
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._

@Ignore
class ServiceSpec extends FlatSpec with Matchers with ScalatestRouteTest with Service {
  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  "Service" should "respond to single trololo query" in {

  }

  it should "respond to trololo pair query" in {

  }

  it should "respond with bad request on incorrect trololo format" in {

  }
}
