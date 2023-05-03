package clients

import models.Models.EventCoefs
import app.Main.ParMap
import cats.effect.IO
import cats.implicits.*
import io.circe.syntax.EncoderOps
import io.circe.generic.auto.*
import org.openqa.selenium.{By, Cookie}
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.Logger

import java.time.Duration
import scala.concurrent.duration.DurationInt

trait BookieClient {

  val log: Logger

  val sports: Seq[String]

  val baseUrl: String

  val sportPageReadinessCssSelector: String

  val eventReadinessCssSelector: String

  def getLiveEventsUrls(driver: RemoteWebDriver): IO[Set[String]]

  def postDriverInitMethod(driver: RemoteWebDriver): Unit

  def postEventPageOpenMethod(driver: RemoteWebDriver): IO[Unit]

  def fetchEventCoefs(driver: RemoteWebDriver, sportName: String): IO[EventCoefs]

  def scanLiveEventsAndRunTracking(
                                    trackedLiveEventUrls: ParMap[String, Unit],
                                    sportName: String,
                                    chromedriver: String,
                                    chromeBinary: String,
                                    headless: Boolean,
                                  ): IO[Unit] = {
    val res = for {
      driverMainPage    <- setupDriver(
        s"$baseUrl/en/$sportName/live",
        sportPageReadinessCssSelector,
        chromedriver,
        chromeBinary,
        headless,
      )
      liveUrls          <- getLiveEventsUrls(driverMainPage)
      _                 <- IO(driverMainPage.quit())
      urlsNotTrackedYet <- IO((liveUrls diff trackedLiveEventUrls.keySet).toSeq)
      _                 <- IO((trackedLiveEventUrls.keySet diff liveUrls).map(trackedLiveEventUrls.remove(_, ())))
      driversMatches    <- urlsNotTrackedYet.map(url =>
        setupDriver(url, eventReadinessCssSelector, chromedriver, chromeBinary, headless)
      ).toList.parSequence
      _                 <- driversMatches.zip(urlsNotTrackedYet)
        .map { case (driver, url) => trackLiveEventCoefs(driver, url, trackedLiveEventUrls, sportName) }.parSequence
    } yield ()
    res.handleError(e => log.error("error on events scanning: ", e))
  }

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
      postDriverInitMethod(driver)
      new WebDriverWait(driver, Duration.ofSeconds(30))
        .until(_.findElement(By.cssSelector(readinessCssSelector)))

      driver
    }
    _   <- IO.sleep(10.seconds)
  } yield drv

  private def trackLiveEventCoefs(
                                   driver: RemoteWebDriver,
                                   url: String,
                                   trackingEvents: ParMap[String, Unit],
                                   sportName: String,
                                 ): IO[Unit] = {
    IO(trackingEvents.putIfAbsent(url, ())) *> {
      for {
        matchEndWaiter <- IO.deferred[Either[Throwable, Unit]]
        _ <- postEventPageOpenMethod(driver)
        _ <- fs2.Stream.awakeEvery[IO](1.second).interruptWhen(matchEndWaiter)
          .foreach { _ =>
            fetchEventCoefs(driver, sportName).flatMap(e => persistEventCoefs(e))
              .handleError(e => log.warn("error on coefs fetching: ", e))
              .start *> IO.whenA(!trackingEvents.keySet.contains(url)) {
               matchEndWaiter.complete(Right(())) *> IO.unit
            }
          }
          .compile.drain
        _ <- IO(driver.quit())
      } yield ()
    }
  }

  private def persistEventCoefs(event: EventCoefs): IO[Unit] = {
    IO(log.info(event.asJson.noSpaces))
  }
}
