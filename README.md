# Deployment:
```
docker build . -t pm-scraper

docker run --name pm-scraper --env SPORTS=<comma separated sports> \ 
 -v <path to logs>:/pm-scraper-logs --restart unless-stopped -d pm-scraper
```
all sports: football,e-sports,basketball,tennis,table-tennis,ice-hockey,volleyball,ufc,handball,boxing,futsal,mma,
australian-rules-football,motor-sport,badminton,baseball,waterpolo,darts,cricket,rugby,billiard,floorball,field-hockey
# Local run:
need to set env vars

`CHROME_BINARY=<path to chrome binary or chrome.exe>`

`CHROMEDRIVER=<path to chromedriver>`

`HEADLESS=false`(optional, true by default)