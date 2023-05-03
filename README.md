# Deployment:
```
docker build . -t pm-scraper && \
docker run --name pm-scraper -v <path to logs>:/pm-scraper-logs --restart unless-stopped -d pm-scraper
```
app parameters can be edited in application.conf file