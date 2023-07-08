package app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxParallelSequence1, toTraverseOps}
import clients.{BookieClient, FirefoxWebDriverImpl, PmClientImpl}
import io.circe.config
import io.circe.generic.auto.deriveDecoder

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  type ParMap[K, V] = collection.concurrent.Map[K, V]

  case class Config(firefoxBinary: String, headlessStr: String, bookies: Seq[BookieConfig]) {
    val headless: Boolean = headlessStr.toBoolean
  }

  case class BookieConfig(name: String, baseUrl: String, sports: Seq[String])

  def parseConfig: IO[Config] = {
    IO.fromEither(config.parser.decode[Config]())
  }

  override def run(args: List[String]): IO[ExitCode] = for {
    cfg: Config                      <- parseConfig
    bookieClients: Seq[BookieClient] = cfg.bookies.map {
      c => c.name match {
        case "pm" =>
          new PmClientImpl(c.sports, c.baseUrl)
      }
    }
    _                                <- bookieClients.map { client =>
      IO.defer {
        val eventsMap = TrieMap.empty[String, Unit]
        client.sports.map { sportName =>
          client
            .scanLiveEventsAndRunTracking(
              eventsMap,
              sportName,
              new FirefoxWebDriverImpl(cfg.firefoxBinary, cfg.headless),
            )
            .start
        }.sequence *> IO.sleep(3.minutes)
      }.foreverM
    }.parSequence
  } yield ExitCode.Success
}
