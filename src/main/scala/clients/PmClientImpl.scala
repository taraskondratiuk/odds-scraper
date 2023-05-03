package clients

import models.Models.{Bet, EventCoefs, Outcome, Score}
import cats.effect.IO
import org.jsoup.Jsoup
import org.openqa.selenium.{By, Cookie}
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.*

class PmClientImpl(override val sports: Seq[String], override val baseUrl: String) extends BookieClient {

  override val log: Logger = LoggerFactory.getLogger("PmScraper")

  override def postDriverInitMethod(driver: RemoteWebDriver): Unit = {
    driver.manage().addCookie(new Cookie("gravitecOptInBlocked", "true"))
  }

  override val sportPageReadinessCssSelector: String = "div[data-id=event-card-container-event]"

  override val eventReadinessCssSelector: String = "div[data-id=heading-bar-title]"

  override def postEventPageOpenMethod(driver: RemoteWebDriver): IO[Unit] = {
    IO {
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
    }.handleError(e => log.warn("failed to expand coefs: ", e))
  }

  override def getLiveEventsUrls(driver: RemoteWebDriver): IO[Set[String]] = IO {
    driver
      .findElements(By.cssSelector("div[data-id=event-card-container-event]"))
      .asScala
      .map(_.findElement(By.cssSelector("a")))
      .toSet
      .map(_.getAttribute("href"))
  }

  override def fetchEventCoefs(driver: RemoteWebDriver, sportName: String): IO[EventCoefs] = IO {
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
          case r =>
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

    EventCoefs(
      sportName,
      discipline,
      tournament.replace(". ", ""),
      competitor1,
      competitor2,
      pageBets,
      scores,
    )
  }
}
