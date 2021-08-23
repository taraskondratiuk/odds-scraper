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
  
      "org.typelevel"                     %% "cats-core"            % "2.6.1",
      "org.typelevel"                     %% "cats-effect"          % "3.2.3",
      "co.fs2"                            %% "fs2-core"             % "3.1.0",
      "co.fs2"                            %% "fs2-io"               % "3.1.0",

      "ch.qos.logback"                    % "logback-classic"       % "1.2.5",
    ).map(_ withSources() withJavadoc())
  )
