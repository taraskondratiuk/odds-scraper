lazy val root = project
  .in(file("."))
  .settings(
    name := "pm-scraper",
    version := "0.1.0",

    scalaVersion := "3.2.0",

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
    ),

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"         % "4.4.0",
      "org.jsoup"                         % "jsoup"                 % "1.15.3",
  
      "org.typelevel"                     %% "cats-core"            % "2.8.0",
      "org.typelevel"                     %% "cats-effect"          % "3.3.14",
      "co.fs2"                            %% "fs2-core"             % "3.2.12",
      "co.fs2"                            %% "fs2-io"               % "3.2.12",

      "io.circe"                          %% "circe-generic"        % "0.14.2",

      "ch.qos.logback"                    % "logback-classic"       % "1.4.0",

      "org.scalatest"                     %% "scalatest"            % "3.2.12" % Test,
    ).map(_ withSources())
  )
