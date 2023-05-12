package clients

import org.openqa.selenium.remote.RemoteWebDriver

trait WebDriverClient {
  val headless: Boolean
  val browserBinaryPath: String
  def setupWebDriver(): RemoteWebDriver
}
