lazy val root = project
  .in(file("."))
  .settings(
    name := "pm-scraper",
    version := "0.1.0",

    scalaVersion := "3.1.0",

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"         % "3.141.59",
      "org.jsoup"                         % "jsoup"                 % "1.14.3",
      "io.github.bonigarcia"              % "webdrivermanager"      % "5.0.2",
  
      "org.typelevel"                     %% "cats-core"            % "2.6.1",
      "org.typelevel"                     %% "cats-effect"          % "3.2.9",
      "co.fs2"                            %% "fs2-core"             % "3.1.6",
      "co.fs2"                            %% "fs2-io"               % "3.1.6",

      "io.circe"                          %% "circe-generic"        % "0.14.1",

      "ch.qos.logback"                    % "logback-classic"       % "1.2.6",
    ).map(_ withSources() withJavadoc())
  )
