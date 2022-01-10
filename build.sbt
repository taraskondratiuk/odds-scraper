lazy val root = project
  .in(file("."))
  .settings(
    name := "pm-scraper",
    version := "0.1.0",

    scalaVersion := "3.1.0",

    scalacOptions ++= Seq(
      "-Xfatal-warnings",
    ),

    libraryDependencies ++= Seq(
      "org.seleniumhq.selenium"           % "selenium-java"         % "3.141.59",
      "org.jsoup"                         % "jsoup"                 % "1.14.3",
  
      "org.typelevel"                     %% "cats-core"            % "2.7.0",
      "org.typelevel"                     %% "cats-effect"          % "3.3.0",
      "co.fs2"                            %% "fs2-core"             % "3.2.4",
      "co.fs2"                            %% "fs2-io"               % "3.2.4",

      "io.circe"                          %% "circe-generic"        % "0.14.1",

      "ch.qos.logback"                    % "logback-classic"       % "1.2.10",

      "org.scalatest"                     %% "scalatest"            % "3.2.10" % Test,
    ).map(_ withSources())
  )
