FROM hseeberger/scala-sbt:8u312_1.6.1_3.1.0

ADD . /pm-scraper/

RUN mkdir pm-scraper-logs

ENV LOG_FILES_DIR /pm-scraper-logs

WORKDIR /pm-scraper

RUN sbt run
