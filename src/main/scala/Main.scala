import cats.effect.*
import cats.implicits.*
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.FirefoxDriver
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
      _      <- (scanLiveEventsAndRunTracking(events).start *> IO.sleep(3.minutes)).foreverM
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
    WebDriverManager.chromedriver().setup()

    val chromeOptions = new ChromeOptions
//    chromeOptions.addArguments("--headless")
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
        _ <- fs2.Stream.awakeEvery[IO](1.second).interruptWhen(matchEndWaiter)
          .foreach { _ =>
            logCurrentCoefs(driver).start *> IO.defer {
              if (!trackingEvents.keySet.contains(url)) matchEndWaiter.complete(Right(())) *> IO.unit
              else IO.unit
            }
          }
          .compile.drain
        _ <- IO(driver.quit())
      } yield ()
    }
  }

  def logCurrentCoefs(driver: RemoteWebDriver): IO[Unit] = IO {

    //todo add coefs info fetching
  }
}
