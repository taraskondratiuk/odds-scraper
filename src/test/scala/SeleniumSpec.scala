import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.scalatest.flatspec.AnyFlatSpec

class SeleniumSpec extends AnyFlatSpec {

  "Selenium" should "run Chrome" in {
    WebDriverManager.chromedriver().setup()

    val chromeOptions = new ChromeOptions
    chromeOptions.addArguments("--headless")
    chromeOptions.addArguments("--disable-dev-shm-usage") // overcome limited resource problems
    chromeOptions.addArguments("--no-sandbox")

    val driver = new ChromeDriver(chromeOptions)
    driver.get("https://www.google.com")
    Thread.sleep(3000)
    val isPageLoaded = driver.getPageSource.contains("<title>Google</title>")
    driver.quit()
    assert(isPageLoaded)
  }
}