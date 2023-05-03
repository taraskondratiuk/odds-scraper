FROM hseeberger/scala-sbt:8u312_1.6.2_3.1.1

RUN mkdir odds-scraper-logs

ADD . /odds-scraper/

WORKDIR /odds-scraper

RUN apt-get update && \
    apt-get --assume-yes install chromium && \
    apt-get --assume-yes install chromium-driver

ENV CHROME_BINARY /usr/bin/chromium

ENV CHROMEDRIVER /usr/bin/chromedriver

ENV LOG_FILES_DIR /odds-scraper-logs

RUN sbt compile

RUN sbt test

CMD ["sbt", "run"]
