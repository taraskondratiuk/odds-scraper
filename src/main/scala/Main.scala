import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
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

  val LOG: Logger = LoggerFactory.getLogger("Scraper")

  type ParMap[K, V] = collection.concurrent.Map[K, V]

  override def run(args: List[String]): IO[ExitCode] = for {
    events <- IO.pure(TrieMap.empty[String, Unit])
    sports = sys.env("SPORTS").split(",").map(_.trim.toLowerCase).toSeq
    _      <- {
      sports.map { sportName =>
        scanLiveEventsAndRunTracking(events, sportName)
          .handleError(e => LOG.error("error on events scanning: ", e))
          .start
      }.sequence *> IO.sleep(3.minutes)
    }.foreverM
  } yield ExitCode.Success

  def scanLiveEventsAndRunTracking(trackedLiveEventUrls: ParMap[String, Unit], sportName: String): IO[Unit] = for {
    driverMainPage    <- setupDriver(
      s"https://parimatch.com/en/$sportName/live",
      "a[data-id=event-card-container-event]",
    )
    liveUrls          <- getLiveEventsUrls(driverMainPage)
    _                 <- IO(driverMainPage.quit())
    urlsNotTrackedYet <- IO((liveUrls diff trackedLiveEventUrls.keySet).toSeq)
    _                 <- IO((trackedLiveEventUrls.keySet diff liveUrls).map(trackedLiveEventUrls.remove(_, ())))
    driversMatches    <- urlsNotTrackedYet
      .map(setupDriver(_, "div[data-id=heading-bar-title]")).toList.parSequence
    _                 <- driversMatches.zip(urlsNotTrackedYet)
      .map { case (driver, url) => trackLiveEventCoefs(driver, url, trackedLiveEventUrls, sportName) }.parSequence
  } yield ()

  def setupDriver(url: String, readinessCssSelector: String): IO[RemoteWebDriver] = for {
    drv <- IO {
      System.setProperty("webdriver.chrome.driver", sys.env("CHROMEDRIVER"))

      val chromeOptions = new ChromeOptions
      chromeOptions.setBinary(sys.env("CHROME_BINARY"))
      if (sys.env.get("HEADLESS").flatMap(_.toBooleanOption).getOrElse(true)) {
        chromeOptions.addArguments("--headless")
      }
      chromeOptions.addArguments("--disable-dev-shm-usage") // overcome limited resource problems
      chromeOptions.addArguments("--no-sandbox")

      val driver = new ChromeDriver(chromeOptions)
      driver.get(url)
      driver.manage().addCookie(new Cookie("gravitecOptInBlocked", "true")) // disable notifications popup
      new WebDriverWait(driver, Duration.ofSeconds(30)).until(_.findElement(By.cssSelector(readinessCssSelector)))

      driver
    }
    _   <- IO.sleep(10.seconds)
  } yield drv

  def getLiveEventsUrls(driver: RemoteWebDriver): IO[Set[String]] = IO {
    driver
      .findElements(By.cssSelector("a[data-id=event-card-container-event]"))
        .asScala
        .toSet
        .map(_.getAttribute("href"))
  }

  def trackLiveEventCoefs(driver: RemoteWebDriver,
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

  def expandCoefs(driver: RemoteWebDriver): IO[Unit] = IO {
    driver
      .findElements(By.cssSelector("button"))
      .asScala
      .find(button => button.findElements(By.cssSelector("span")).asScala.headOption.exists(el => el.getText == "OK"))
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

  def logCurrentCoefs(driver: RemoteWebDriver, sportName: String): IO[Unit] = IO {
    val fullPage = Jsoup.parse(driver.findElement(By.ById("root")).getAttribute("innerHTML"))

    val title = fullPage.selectFirst("div[data-id=heading-bar-title]").text()
    val (discipline, tournament) = title.splitAt(title.indexOf(". "))

    val competitor1 = fullPage.selectFirst("div[data-id*=competitor-home]").text()
    val competitor2 = fullPage.selectFirst("div[data-id*=competitor-away]").text()

    val scores = Try {
      fullPage
        .selectFirst("div[data-id*=competitor-home]")
        .parent()
        .nextElementSibling()
        .children()
        .asScala
        .toSeq
        .map { mapScoreEl =>
          mapScoreEl.children().asScala.toList.map(_.text()) match {
            case map :: score1 :: score2 :: _ =>
              Score(map, score1, score2)
            case r                            =>
              throw new Exception(s"match error 1 for scores fetch, failed to parse: $r")
          }
        }
    }.orElse(Try {
      fullPage
        .selectFirst("div[data-id*=competitor-home]")
        .parent()
        .parent()
        .nextElementSibling()
        .children()
        .asScala
        .toList
        .map(_.text()) match {
          case map :: score1 :: score2 :: _ =>
            Seq(Score(map, score1, score2))
          case r                            =>
            throw new Exception(s"match error 2 for scores fetch, failed to parse: $r")
        }
    }).get
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
