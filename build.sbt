ThisBuild / tlBaseVersion := "0.5"
ThisBuild / organization := "org.polyvariant.smithy4s-bsp"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / tlJdkRelease := Some(21)
ThisBuild / tlFatalWarnings := false
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

val jsonrpclibVersion = "0.0.8+51-32e30ec0-SNAPSHOT"

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
    "org.typelevel" %%% "weaver-cats" % "0.9.1" % Test
  ),
)

lazy val protocol = project
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-model" % "1.60.3"
    ),
    smithyTraitCodegenNamespace := "smithy4sbsp.meta",
    smithyTraitCodegenJavaPackage := "smithy4sbsp.meta",
    smithyTraitCodegenDependencies := Nil,
  )
  .enablePlugins(SmithyTraitCodegenPlugin)
  .enablePlugins(NoPublishPlugin)

lazy val transformation = project
  .settings(
    commonSettings,
    scalaVersion := "2.12.20",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % "1.60.3",
      "software.amazon.smithy" % "smithy-syntax" % "1.60.3",
      "ch.epfl.scala" % "spec-traits" % "2.2.0-M2",
      "tech.neander" % "jsonrpclib-smithy" % jsonrpclibVersion,
      "com.disneystreaming.alloy" % "alloy-core" % "0.3.21",
      "com.disneystreaming.smithy4s" % "smithy4s-protocol" % smithy4sVersion.value,
      "com.lihaoyi" %% "os-lib" % "0.11.4" % Test,
      "software.amazon.smithy" % "smithy-diff" % "1.60.3" % Test,
    ),
    publish / skip := true,
  )
  .dependsOn(protocol)
  .enablePlugins(NoPublishPlugin)

lazy val codegen = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "ch.epfl.scala" % "spec" % "2.2.0-M2" % Smithy4s,
      "ch.epfl.scala" % "spec-traits" % "2.2.0-M2" % Smithy4s,
      "tech.neander" % "jsonrpclib-smithy" % jsonrpclibVersion % Smithy4s,
      "com.disneystreaming.smithy4s" %%% "smithy4s-core" % smithy4sVersion.value,
    ),
    Compile / smithy4sModelTransformers := List(
      "untagged-unions",
      "set-shapes",
      "open-enums",
      "transform-build-target-data",
      "transform-jsonrpclib-traits",
      "add-http",
      "rename-scala-namespace",
    ),
    Compile / smithy4sAllDependenciesAsJars += (protocol / Compile / packageBin).value,
    Compile / smithy4sAllDependenciesAsJars += (transformation / Compile / packageBin).value,
  )
  .enablePlugins(Smithy4sCodegenPlugin)

lazy val bsp4s = project
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "tech.neander" %%% "jsonrpclib-smithy4s" % jsonrpclibVersion,
      "io.circe" %%% "circe-parser" % "0.14.14",
      "io.circe" %%% "circe-literal" % "0.14.14",
    ),
  )
  .dependsOn(codegen)

lazy val examples = project
  .settings(
    fork := true,
    commonSettings,
    libraryDependencies ++= Seq(
      "tech.neander" %%% "jsonrpclib-fs2" % jsonrpclibVersion,
      "co.fs2" %%% "fs2-io" % "3.12.0",
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value,
      "org.typelevel" %%% "weaver-cats" % "0.9.1" % Test,
    ),
    name := "sample-server",
  )
  .dependsOn(bsp4s)
  .enablePlugins(NoPublishPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(bsp4s, examples, codegen, transformation, protocol)
  .enablePlugins(NoPublishPlugin)
