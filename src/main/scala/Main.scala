import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.jsoup.Jsoup
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.{By, Cookie, WebDriver}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.*
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  val LOG = LoggerFactory.getLogger("Scraper")

  override def run(args: List[String]): IO[ExitCode] =
    for {
      events <- IO.pure(TrieMap.empty[String, Unit])
      _      <- (scanLiveEventsAndRunTracking(events).handleError(e => LOG.error("error on events scanning: ", e)).start *> IO.sleep(3.minutes)).foreverM
    } yield ExitCode.Success

  def scanLiveEventsAndRunTracking(trackedLiveEventUrls: collection.concurrent.Map[String, Unit]): IO[Unit] = for {
    driverMainPage    <- setupDriver("https://parimatch.com/en/e-sports/live", "a[data-id=event-card-container-event]")
    liveUrls          <- getLiveEventsUrls(driverMainPage)
    _                 <- IO(driverMainPage.quit())
    urlsNotTrackedYet <- IO((liveUrls diff trackedLiveEventUrls.keySet).toSeq)
    _                 <- IO((trackedLiveEventUrls.keySet diff liveUrls).map(trackedLiveEventUrls.remove(_, ())))
    driversMatches    <- urlsNotTrackedYet.map(setupDriver(_, "div[data-id=heading-bar-title]")).toList.parSequence
    _                 <- driversMatches.zip(urlsNotTrackedYet)
      .map { case (driver, url) => trackLiveEventCoefs(driver, url, trackedLiveEventUrls) }.parSequence
  } yield ()

  def setupDriver(url: String, readinessCssSelector: String): IO[RemoteWebDriver] = IO {
    System.setProperty("webdriver.chrome.driver", sys.env("CHROMEDRIVER"))
    System.setProperty("webdriver.chrome.whitelistedIps", "")
    System.setProperty("webdriver.chrome.verboseLogging", "true")

    val chromeOptions = new ChromeOptions
    chromeOptions.setBinary(sys.env("CHROME_BINARY"))
    chromeOptions.addArguments("--headless")
    chromeOptions.addArguments("--disable-dev-shm-usage") // overcome limited resource problems
    chromeOptions.addArguments("--no-sandbox")

    val driver = new ChromeDriver(chromeOptions)
    driver.get(url)
    driver.manage().addCookie(new Cookie("gravitecOptInBlocked", "true")) // disable notifications popup
    new WebDriverWait(driver, 30).until(_.findElement(By.cssSelector(readinessCssSelector)))

    driver
  }

  def getLiveEventsUrls(driver: RemoteWebDriver): IO[Set[String]] = IO {
    driver
      .findElementsByCssSelector("div[data-id=live-event-list]")
      .asScala.toSet
      .flatMap(_.findElements(By.cssSelector("a[data-id=event-card-container-event]")).asScala.map(_.getAttribute("href")))
  }

  def trackLiveEventCoefs(driver: RemoteWebDriver, url: String, trackingEvents: collection.concurrent.Map[String, Unit]): IO[Unit] = {
    IO(trackingEvents.putIfAbsent(url, ())) *> {
      for {
        matchEndWaiter <- IO.deferred[Either[Throwable, Unit]]
        _              <- expandCoefs(driver).handleError(e => LOG.warn("failed to expand coefs: ", e))
        _              <- fs2.Stream.awakeEvery[IO](1.second).interruptWhen(matchEndWaiter)
          .foreach { _ =>
            logCurrentCoefs(driver).handleError(e => LOG.warn("error on coefs fetching: ", e)).start *> IO.defer {
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
    driver.findElement(By.cssSelector("div[data-id=event-markets-tab-all]")).click()
    driver
      .findElements(By.cssSelector("svg"))
      .asScala
      .filter(svg => svg.findElements(By.cssSelector("use")).asScala.filter(_.getAttribute("xlink:href") == "#UII_ExpandMore").nonEmpty)
      .foreach(_.click())
  }

  def logCurrentCoefs(driver: RemoteWebDriver): IO[Unit] = IO {
    val fullPage = Jsoup.parse(driver.findElement(By.ById("root")).getAttribute("innerHTML"))

    val title = fullPage.selectFirst("div[data-id=heading-bar-title]").text()
    val (discipline, tournament) = title.splitAt(title.indexOf(". "))

    val competitor1 = fullPage.selectFirst("div[data-id=competitor-home]").text()
    val competitor2 = fullPage.selectFirst("div[data-id=competitor-away]").text()


    val scores = fullPage
      .selectFirst("div[data-id=competitor-home]")
      .parent()
      .nextElementSibling()
      .children()
      .asScala
      .toSeq
      .map { mapScoreEl =>
        val (map :: score1 :: score2 :: _) = mapScoreEl.children().asScala.toList.map(_.text())
        Score(map, score1, score2)
      }

    val maybePageBetsEl = Option(fullPage.selectFirst("div[data-id=event-market-tabs-carousel]"))

    val pageBets = maybePageBetsEl.fold(Seq.empty[Bet]) { page =>
      page
        .siblingElements()
        .asScala
        .drop(1)
        .takeWhile(_.child(0).attr("data-id") != "footer-wrapper")
        .toSeq
        .map { el =>
          val betName = el
            .selectFirst("div[data-id^=market-expansion-panel-header]")
            .attr("data-id")
            .replace("market-expansion-panel-header-", "")
          val outcomes = el
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
          Bet(betName, outcomes)
        }
    }

    LOG.info(Event(discipline, tournament.replace(". ", ""), competitor1, competitor2, pageBets, scores).asJson.noSpaces)
  }
}
