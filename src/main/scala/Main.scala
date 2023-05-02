import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import io.circe.config
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.jsoup.Jsoup
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.{By, Cookie, WebDriver}
import org.slf4j.{Logger, LoggerFactory}

import java.time.Duration
import scala.jdk.CollectionConverters.*
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.util.Try

object Main extends IOApp {

  private val LOG: Logger = LoggerFactory.getLogger("Scraper")

  private type ParMap[K, V] = collection.concurrent.Map[K, V]

  private case class Config(
                             sports: Seq[String],
                             chromedriver: String,
                             chromeBinary: String,
                             headlessStr: String,
                             pmBaseUrl: String,
                           ) {
    val headless: Boolean = headlessStr.toBoolean
  }

  override def run(args: List[String]): IO[ExitCode] = for {
    cfg    <- IO.fromEither(config.parser.decode[Config]())
    events <- IO.pure(TrieMap.empty[String, Unit])
    _      <- {
      cfg.sports.map { sportName =>
        scanLiveEventsAndRunTracking(events, sportName, cfg.chromedriver, cfg.chromeBinary, cfg.pmBaseUrl, cfg.headless)
          .handleError(e => LOG.error("error on events scanning: ", e))
          .start
      }.sequence *> IO.sleep(3.minutes)
    }.foreverM
  } yield ExitCode.Success

  private def scanLiveEventsAndRunTracking(
                                            trackedLiveEventUrls: ParMap[String, Unit],
                                            sportName: String,
                                            chromedriver: String,
                                            chromeBinary: String,
                                            pmBaseUrl: String,
                                            headless: Boolean,
                                          ): IO[Unit] = for {
    driverMainPage    <- setupDriver(
      s"$pmBaseUrl/en/$sportName/live",
      "div[data-id=event-card-container-event]",
      chromedriver,
      chromeBinary,
      headless,
    )
    liveUrls          <- getLiveEventsUrls(driverMainPage)
    _                 <- IO(driverMainPage.quit())
    urlsNotTrackedYet <- IO((liveUrls diff trackedLiveEventUrls.keySet).toSeq)
    _                 <- IO((trackedLiveEventUrls.keySet diff liveUrls).map(trackedLiveEventUrls.remove(_, ())))
    driversMatches    <- urlsNotTrackedYet.map(url =>
      setupDriver(url, "div[data-id=heading-bar-title]", chromedriver, chromeBinary, headless)
    ).toList.parSequence
    _                 <- driversMatches.zip(urlsNotTrackedYet)
      .map { case (driver, url) => trackLiveEventCoefs(driver, url, trackedLiveEventUrls, sportName) }.parSequence
  } yield ()

  private def setupDriver(
                           url: String,
                           readinessCssSelector: String,
                           chromedriver: String,
                           chromeBinary: String,
                           headless: Boolean,
                         ): IO[RemoteWebDriver] = for {
    drv <- IO {
      System.setProperty("webdriver.chrome.driver", chromedriver)

      val chromeOptions = new ChromeOptions
      chromeOptions.setBinary(chromeBinary)
      if (headless) {
        chromeOptions.addArguments("--headless")
      }
      chromeOptions.addArguments("--disable-dev-shm-usage") // overcome limited resource problems
      chromeOptions.addArguments("--no-sandbox")
      chromeOptions.addArguments("--remote-allow-origins=*")

      val driver = new ChromeDriver(chromeOptions)
      driver.get(url)
      driver.manage().addCookie(new Cookie("gravitecOptInBlocked", "true")) // disable notifications popup
      new WebDriverWait(driver, Duration.ofSeconds(30))
        .until(_.findElement(By.cssSelector(readinessCssSelector)))

      driver
    }
    _   <- IO.sleep(10.seconds)
  } yield drv

  private def getLiveEventsUrls(driver: RemoteWebDriver): IO[Set[String]] = IO {
    driver
      .findElements(By.cssSelector("div[data-id=event-card-container-event]"))
      .asScala
      .map(_.findElement(By.cssSelector("a")))
      .toSet
      .map(_.getAttribute("href"))
  }

  private def trackLiveEventCoefs(driver: RemoteWebDriver,
                                  url: String,
                                  trackingEvents: ParMap[String, Unit],
                                  sportName: String,
                                 ): IO[Unit] = {
    IO(trackingEvents.putIfAbsent(url, ())) *> {
      for {
        matchEndWaiter <- IO.deferred[Either[Throwable, Unit]]
        _              <- expandCoefs(driver).handleError(e => LOG.warn("failed to expand coefs: ", e))
        _              <- fs2.Stream.awakeEvery[IO](1.second).interruptWhen(matchEndWaiter)
          .foreach { _ =>
            logCurrentCoefs(driver, sportName)
              .handleError(e => LOG.warn("error on coefs fetching: ", e))
              .start *> IO.defer {
              if (!trackingEvents.keySet.contains(url)) matchEndWaiter.complete(Right(())) *> IO.unit
              else IO.unit
            }
          }
          .compile.drain
        _              <- IO(driver.quit())
      } yield ()
    }
  }

  private def expandCoefs(driver: RemoteWebDriver): IO[Unit] = IO {
    driver
      .findElements(By.cssSelector("button"))
      .asScala
      .find(button =>
        button.findElements(By.cssSelector("span")).asScala.headOption.exists(el => el.getText == "OK")
      )
      .foreach(_.click())
    driver.findElement(By.cssSelector("div[data-id=event-markets-tab-0]")).click()
    driver
      .findElements(By.cssSelector("svg"))
      .asScala
      .filter(svg => svg
        .findElements(By.cssSelector("use"))
        .asScala
        .exists(_.getAttribute("xlink:href") == "#UII_ExpandMore")
      )
      .foreach(_.click())
  }

  private def logCurrentCoefs(driver: RemoteWebDriver, sportName: String): IO[Unit] = IO {
    val fullPage = Jsoup.parse(driver.findElement(By.ById("root")).getAttribute("innerHTML"))

    val title = fullPage.selectFirst("div[data-id=heading-bar-title]").text()
    val (discipline, tournament) = title.splitAt(title.indexOf(". "))

    val competitor1 = fullPage.selectFirst("span[data-id=competitor-home]").selectFirst("a").text()
    val competitor2 = fullPage.selectFirst("span[data-id=competitor-away]").selectFirst("a").text()

    val scores = fullPage
      .selectFirst("div[data-id=live-infoboard]")
      .child(1)
      .children()
      .asScala
      .toSeq
      .map { mapScoreEl =>
        mapScoreEl.children.text().split(" ").toList match {
          case map :: score1 :: score2 :: _ =>
            Score(map, score1, score2)
          case r                            =>
            throw new Exception(s"match error 1 for scores fetch, failed to parse: $r")
        }
      }

    val maybePageBetsEl = Option(fullPage.selectFirst("div[data-id=event-market-tabs-carousel]"))

    val pageBets = maybePageBetsEl.fold(Seq.empty[Bet]) { page =>
      page
        .siblingElements()
        .asScala
        .toSeq
        .flatMap { el =>
          for {
            betName <- {
              el
                .select("div[data-id^=market-expansion-panel-header]")
                .asScala
                .headOption
                .map(_.attr("data-id").replace("market-expansion-panel-header-", ""))
            }
            outcomes <- Some {
              el
                .select("div[data-id=outcome]")
                .asScala
                .toSeq
                .map { outcome =>
                  val nameInitial = outcome.child(0).text()
                  val name = if (betName.toLowerCase.contains("total") && !betName.toLowerCase.contains("even")) {
                    val totalNum = outcome.siblingElements().get(0).text()
                    val overOrUnder = if (outcome == outcome.parent().child(1)) "over" else "under"
                    s"$betName $overOrUnder $totalNum"
                  } else nameInitial

                  val coef = outcome.selectFirst("span").text().toFloat
                  Outcome(name, coef)
                }
            }
          } yield Bet(betName, outcomes)
        }
    }

    LOG.info(Event(
      sportName,
      discipline,
      tournament.replace(". ", ""),
      competitor1,
      competitor2,
      pageBets,
      scores,
    ).asJson.noSpaces)
  }
}
