package app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import clients.{BookieClient, PmClientImpl}
import io.circe.config
import io.circe.generic.auto.*
import io.circe.syntax.*
import models.Models.{Bet, EventCoefs, Outcome, Score}
import org.jsoup.Jsoup
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.{By, Cookie, WebDriver}
import org.slf4j.{Logger, LoggerFactory}

import java.time.Duration
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.util.Try

object Main extends IOApp {

  type ParMap[K, V] = collection.concurrent.Map[K, V]

  case class Config(chromedriver: String, chromeBinary: String, headlessStr: String, bookies: Seq[BookieConfig]) {
    val headless: Boolean = headlessStr.toBoolean
  }

  case class BookieConfig(name: String, baseUrl: String, sports: Seq[String])

  def parseConfig: IO[Config] = {
    IO.fromEither(config.parser.decode[Config]())
  }

  override def run(args: List[String]): IO[ExitCode] = for {
    cfg                              <- parseConfig
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
            .scanLiveEventsAndRunTracking(eventsMap, sportName, cfg.chromedriver, cfg.chromeBinary, cfg.headless)
            .start
        }.sequence *> IO.sleep(3.minutes)
      }.foreverM
    }.parSequence
  } yield ExitCode.Success
}
