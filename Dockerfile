FROM hseeberger/scala-sbt:8u312_1.6.1_3.1.0

RUN mkdir pm-scraper-logs

ENV LOG_FILES_DIR /pm-scraper-logs

ADD . /pm-scraper/

WORKDIR /pm-scraper

RUN apt-get update && \
    apt-get install libavcodec58 && \
    apt-get install -f -y ./src/main/resources/chromium_93.0.4577.82-1_arm64.deb

RUN rm src/main/resources/chromium_93.0.4577.82-1_arm64.deb

RUN sbt compile

RUN sbt test

CMD ["sbt", "run"]
