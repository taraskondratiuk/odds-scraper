import app.Main
import app.Main.Config
import cats.effect.unsafe.implicits.global
import clients.{FirefoxWebDriverImpl, PmClientImpl}
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.IO

class ScraperSpec extends AnyFlatSpec {

  var cfg: Config = null

  "Config" should "be set properly" in {
    cfg = Main.parseConfig.unsafeRunSync()
  }

  "Selenium" should "run browser" in {
    val driver = new FirefoxWebDriverImpl(cfg.firefoxBinary, cfg.headless).setupWebDriver()
    driver.get("https://www.google.com")
    Thread.sleep(3000)
    val isPageLoaded = driver.getPageSource.contains("<title>Google</title>")
    driver.quit()
    assert(isPageLoaded)
  }

  "Selenium" should "extract coefs" in {
    val driverClient = new FirefoxWebDriverImpl(cfg.firefoxBinary, cfg.headless)
    val pmClient = new PmClientImpl(Seq("football"), cfg.bookies.find(_.name == "pm").get.baseUrl)

    val res = for {
      driverMainPage <- pmClient
        .setupDriver(s"${pmClient.baseUrl}/en/${pmClient.sports.head}/live", pmClient.sportPageReadinessCssSelector, driverClient)
      _              = println("=======step 1")     
      eventUrl       <- pmClient.getLiveEventsUrls(driverMainPage).map(_.head)
      _              = println("=======step 2")
      _              <- IO(driverMainPage.quit())
      _              = println("=======step 3")
      eventDriver    <- pmClient.setupDriver(eventUrl, pmClient.eventReadinessCssSelector, driverClient)
      _              = println("=======step 4")
      _              <- pmClient.postEventPageOpenMethod(eventDriver)
      _              = println("=======step 5")
      coefs          <- pmClient.fetchEventCoefs(eventDriver, "football")
      _              = println("=======step 6")
      _              <- IO(eventDriver.quit())
      _              = println(s"coefs fetched: $coefs")
    } yield ()
    res.unsafeRunSync() 
  }
}
