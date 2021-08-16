lazy val root = project
  .in(file("."))
  .settings(
    name := "pm-scraper",
    version := "0.1.0",

    scalaVersion := "3.0.1",

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"         % "3.141.59",
      "org.jsoup"                         % "jsoup"                 % "1.14.2",
      "io.github.bonigarcia"              % "webdrivermanager"      % "4.4.3",
  
      "ch.qos.logback"                    % "logback-classic"       % "1.2.5",
    )
  )
