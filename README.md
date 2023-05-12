# Deployment:
```
docker build . -t odds-scraper && \
docker run --name odds-scraper -v <path to logs>:/odds-scraper-logs --restart unless-stopped -d odds-scraper
```
app parameters can be edited in src/main/resources/application.conf file
