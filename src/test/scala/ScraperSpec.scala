import app.Main
import app.Main.Config
import cats.effect.unsafe.implicits.global
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.scalatest.flatspec.AnyFlatSpec

class ScraperSpec extends AnyFlatSpec {

  var cfg: Config = null

  "Config" should "be set properly" in {
    cfg = Main.parseConfig.unsafeRunSync()
  }

  "Selenium" should "run Chrome" in {
    System.setProperty("webdriver.chrome.driver", cfg.chromedriver)

    val chromeOptions = new ChromeOptions
    chromeOptions.setBinary(cfg.chromeBinary)
    chromeOptions.addArguments("--headless")
    chromeOptions.addArguments("--disable-dev-shm-usage") // overcome limited resource problems
    chromeOptions.addArguments("--no-sandbox")
    chromeOptions.addArguments("--remote-allow-origins=*")

    val driver = new ChromeDriver(chromeOptions)
    driver.get("https://www.google.com")
    Thread.sleep(3000)
    val isPageLoaded = driver.getPageSource.contains("<title>Google</title>")
    driver.quit()
    assert(isPageLoaded)
  }
}
