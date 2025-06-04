ThisBuild / tlBaseVersion := "0.4"
ThisBuild / organization := "org.polyvariant.smithy4s-bsp"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatype01

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / scalaVersion := "3.3.5"
ThisBuild / tlJdkRelease := Some(21)
ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiDependencyGraphJob := false
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

val commonSettings = Seq(
  scalacOptions -= "-Ykind-projector:underscores",
  scalacOptions ++= {
    if (scalaVersion.value.startsWith("3"))
      Seq(
        "-Ykind-projector",
        "-deprecation",
        "-Wunused:all",
        "-Wnonunit-statement",
      )
    else
      Nil
  },
  libraryDependencies ++= Seq(
    "com.disneystreaming" %%% "weaver-cats" % "0.8.4" % Test
  ),
)

lazy val transformation = project
  .settings(
    commonSettings,
    scalaVersion := "2.13.16",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % "1.57.1",
      "software.amazon.smithy" % "smithy-syntax" % "1.57.1",
      "ch.epfl.scala" % "spec-traits" % "2.2.0-M2",
      "tech.neander" % "jsonrpclib-smithy" % "0.0.8+26-13de833b-SNAPSHOT",
      "com.disneystreaming.alloy" % "alloy-core" % "0.3.19",
      "com.disneystreaming.smithy4s" % "smithy4s-protocol" % smithy4sVersion.value,
      "com.lihaoyi" %% "os-lib" % "0.11.4" % Test,
      "software.amazon.smithy" % "smithy-diff" % "1.57.1" % Test,
    ),
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
    mimaFailOnNoPrevious := false,
  )

lazy val codegen = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "ch.epfl.scala" % "spec" % "2.2.0-M2" % Smithy4s,
      "ch.epfl.scala" % "spec-traits" % "2.2.0-M2" % Smithy4s,
      "tech.neander" % "jsonrpclib-smithy" % "0.0.8+26-13de833b-SNAPSHOT" % Smithy4s,
      "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
    ),
    Compile / smithy4sModelTransformers := List(
      "untagged-unions",
      "set-shapes",
      "open-enums",
      "transform-build-target-data",
      "transform-jsonrpclib-traits",
      "rename-scala-namespace",
    ),
    Compile / smithy4sAllDependenciesAsJars += (transformation / Compile / packageBin).value,
  )
  .enablePlugins(Smithy4sCodegenPlugin)

lazy val bsp4s = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "tech.neander" %%% "jsonrpclib-smithy4s" % "0.0.8+26-13de833b-SNAPSHOT",
      "io.circe" %%% "circe-parser" % "0.14.13",
      "io.circe" %%% "circe-literal" % "0.14.13",
    ),
  )
  .dependsOn(codegen)

lazy val sampleServer = project
  .settings(
    fork := true,
    commonSettings,
    libraryDependencies ++= Seq(
      "tech.neander" %%% "jsonrpclib-fs2" % "0.0.8+26-13de833b-SNAPSHOT",
      "co.fs2" %%% "fs2-io" % "3.12.0",
      "tech.neander" %%% "jsonrpclib-fs2" % "0.0.8+26-13de833b-SNAPSHOT",
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value,
      "com.disneystreaming" %%% "weaver-cats" % "0.8.4" % Test,
    ),
    name := "sample-server",
    mimaPreviousArtifacts := Set.empty,
    mimaFailOnNoPrevious := false,
  )
  .dependsOn(bsp4s)

lazy val root = project
  .in(file("."))
  .aggregate(bsp4s, sampleServer, codegen, transformation)
  .settings(
    sonatypeProfileName := "org.polyvariant"
  )
  .enablePlugins(NoPublishPlugin)
