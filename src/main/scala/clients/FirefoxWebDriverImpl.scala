package clients

import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.remote.RemoteWebDriver

class FirefoxWebDriverImpl(
  override val browserBinaryPath: String,
  override val headless: Boolean,
  ) extends WebDriverClient {

  override def setupWebDriver(): RemoteWebDriver = {
    WebDriverManager.firefoxdriver().setup()
    val firefoxOptions = new FirefoxOptions
    firefoxOptions.setBinary(browserBinaryPath)
    if (headless) {
      firefoxOptions.addArguments("-headless")
    }
    new FirefoxDriver(firefoxOptions)
  }
}
