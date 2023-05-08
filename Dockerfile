FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_3.2.2

RUN mkdir odds-scraper-logs

ADD . /odds-scraper/

WORKDIR /odds-scraper

RUN apt-get update && \
    apt-get --assume-yes install chromium-browser && \
    apt-get --assume-yes install chromium-driver

ENV CHROME_BINARY /usr/bin/chromium-browser

ENV CHROMEDRIVER /usr/bin/chromedriver

ENV LOG_FILES_DIR /odds-scraper-logs

RUN sbt compile

RUN sbt test

CMD ["sbt", "run"]
