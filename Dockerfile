FROM hseeberger/scala-sbt:8u312_1.6.1_3.1.0

ADD . /pm-scraper/

ENV LOG_FILES_DIR logs

RUN sbt run
