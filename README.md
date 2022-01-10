# Deployment:
```
docker build . -t pm-scraper
docker run --name pm-scraper -v <path to logs>:/pm-scraper-logs --restart unless-stopped -d pm-scraper
```
# Local run:
need to set env vars

`CHROME_BINARY=<path to chrome binary or chrome.exe>`

`CHROMEDRIVER=<path to chromedriver>`