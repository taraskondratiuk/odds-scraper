lazy val root = project
  .in(file("."))
  .settings(
    name := "pm-scraper",
    version := "0.1.0",

    scalaVersion := "3.1.1",

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
    ),

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"         % "4.1.4",
      "org.jsoup"                         % "jsoup"                 % "1.14.3",
  
      "org.typelevel"                     %% "cats-core"            % "2.7.0",
      "org.typelevel"                     %% "cats-effect"          % "3.3.11",
      "co.fs2"                            %% "fs2-core"             % "3.2.7",
      "co.fs2"                            %% "fs2-io"               % "3.2.7",

      "io.circe"                          %% "circe-generic"        % "0.14.1",

      "ch.qos.logback"                    % "logback-classic"       % "1.2.11",

      "org.scalatest"                     %% "scalatest"            % "3.2.12" % Test,
    ).map(_ withSources())
  )
