FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_3.2.2

RUN mkdir odds-scraper-logs

ADD . /odds-scraper/

WORKDIR /odds-scraper

RUN apt-get update

RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb

RUN apt -y install ./google-chrome-stable_current_amd64.deb

RUN rm google-chrome-stable_current_amd64.deb

RUN wget https://chromedriver.storage.googleapis.com/$(google-chrome --version | grep -oP "[0-9.]{10,20}")/chromedriver_linux64.zip

RUN apt-get -y install zip

RUN unzip chromedriver_linux64.zip

RUN rm chromedriver_linux64.zip

RUN mv chromedriver /usr/bin/chromedriver

ENV CHROME_BINARY /opt/google/chrome/chrome

ENV CHROMEDRIVER /usr/bin/chromedriver

ENV LOG_FILES_DIR /odds-scraper-logs

RUN sbt compile

RUN sbt test

CMD ["sbt", "run"]
