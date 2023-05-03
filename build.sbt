lazy val root = project
  .in(file("."))
  .settings(
    name := "odds-scraper",
    version := "0.1.0",

    scalaVersion := "3.2.2",

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
    ),

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"                 % "4.8.1",
      "org.jsoup"                         % "jsoup"                         % "1.15.4",
  
      "org.typelevel"                     %% "cats-core"                    % "2.9.0",
      "org.typelevel"                     %% "cats-effect"                  % "3.4.8",
      "co.fs2"                            %% "fs2-core"                     % "3.6.1",
      "co.fs2"                            %% "fs2-io"                       % "3.6.1",

      "io.circe"                          %% "circe-generic"                % "0.14.5",
      "io.circe"                          %% "circe-config"                 % "0.10.0",

      "ch.qos.logback"                    % "logback-classic"               % "1.4.6",

      "org.scalatest"                     %% "scalatest"                    % "3.2.15" % Test,
    ).map(_ withSources())
  )
