lazy val root = project
  .in(file("."))
  .settings(
    name := "odds-scraper",
    version := "0.1.0",

    scalaVersion := "3.3.0",

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
    ),

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"                 % "4.8.1",
      "io.github.bonigarcia"              % "webdrivermanager"              % "5.3.2",

      "org.jsoup"                         % "jsoup"                         % "1.15.4",
  
      "org.typelevel"                     %% "cats-core"                    % "2.9.0",
      "org.typelevel"                     %% "cats-effect"                  % "3.5.0",
      "co.fs2"                            %% "fs2-core"                     % "3.7.0",
      "co.fs2"                            %% "fs2-io"                       % "3.7.0",

      "io.circe"                          %% "circe-generic"                % "0.14.5",
      "io.circe"                          %% "circe-config"                 % "0.10.0",

      "ch.qos.logback"                    % "logback-classic"               % "1.4.7",

      "org.scalatest"                     %% "scalatest"                    % "3.2.15" % Test,
    ).map(_ withSources())
  )
