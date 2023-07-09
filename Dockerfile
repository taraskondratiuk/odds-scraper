FROM sbtscala/scala-sbt:eclipse-temurin-focal-17.0.5_8_1.9.1_3.3.0

RUN mkdir odds-scraper-logs

ADD . /odds-scraper/

WORKDIR /odds-scraper

RUN apt-get update

RUN apt-get -y install firefox

ENV FIREFOX_BINARY /usr/bin/firefox

ENV LOG_FILES_DIR /odds-scraper-logs

RUN sbt compile

RUN sbt test

CMD ["sbt", "run"]
