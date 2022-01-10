# Deployment:
```
docker build . -t pm-scraper
docker run -d pm-scraper
```
# Local run:
need to set env vars

`CHROME_BINARY=<path to chrome binary or chrome.exe>`

`CHROMEDRIVER=<path to chromedriver>`