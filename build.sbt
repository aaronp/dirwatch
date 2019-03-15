name := "dirwatch"

libraryDependencies ++= List(

   "com.github.aaronp"          %% "eie"             % "0.0.3",
   "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2",
   "io.monix"                   %% "monix-reactive"  % "3.0.0-RC2",
   "ch.qos.logback"              % "logback-classic" % "1.2.3",
   "com.typesafe"                % "config"          % "1.3.2",

   "junit"                  %  "junit"     % "4.12"  % "test",
   "org.scalatest"          %% "scalatest" % "3.0.5" % "test",
   "org.scala-lang.modules" %% "scala-xml" % "1.1.1" % "test"
)

