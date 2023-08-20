# Deployment:
```
docker build . -t odds-scraper && \
docker run --name odds-scraper --net=<network name> --log-driver json-file --log-opt tag="{{.ImageName}}|{{.Name}}|{{.ImageFullID}}|{{.FullID}}" -v <path to logs>:/odds-scraper-logs --restart unless-stopped -d odds-scraper
```
app parameters can be edited in src/main/resources/application.conf file
