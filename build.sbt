val username            = "aaronp"
val repo = "dirwatch"
organization := s"com.github.${username}"

name := repo

enablePlugins(BuildInfoPlugin)
enablePlugins(DockerPlugin)

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

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "dirwatch.build"

pomIncludeRepository := (_ => false)

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/${username}/${repo}
  </url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <id>
          ${username}
        </id>
        <name>Aaron Pritzlaff</name>
        <url>https://github.com/${username}/${repo}
        </url>
      </developer>
    </developers>
}




imageNames in docker := Seq(
  ImageName(s"porpoiseltd/${name.value}:latest")
)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value

  val resDir         = (resourceDirectory in Compile).value

  val appDir = "/app"

  streams.value.log.info(s"Base Dir is ${baseDirectory.value.toPath}")

  //
  // see https://forums.docker.com/t/is-it-possible-to-pass-arguments-in-dockerfile/14488
  // for passing in args to docker in run (which basically just says to use $@)
  //
  val dockerFile = new Dockerfile {
    from("java")
    maintainer(developers.value.map(_.name).headOption.getOrElse(organization.value))
    run("mkdir", "-p", s"$appDir/data")
    env("DATA_DIR", s"$appDir/data/")
    volume(s"$appDir/data")
    add(artifact, s"$appDir/dirscan.jar")
    workDir(s"$appDir")
    entryPoint("java", "-cp", "dirscan.jar", "dirscan.Main")
  }

  sLog.value.info(s"Created dockerfile: ${dockerFile.instructions}")

  dockerFile
}

