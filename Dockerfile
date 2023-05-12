FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_3.2.2

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
