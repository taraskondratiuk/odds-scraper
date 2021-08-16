import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.FirefoxDriver

@main def pmScraper: Unit =
  WebDriverManager.chromedriver().setup()

  val chromeOptions = new ChromeOptions
  
//  chromeOptions.addArguments("--headless")
  chromeOptions.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
  chromeOptions.addArguments("--no-sandbox");
  
  val driver = new ChromeDriver(chromeOptions)
  
  driver.get("https://www.google.com")
