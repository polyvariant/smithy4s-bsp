ThisBuild / tlBaseVersion := "0.6"
ThisBuild / organization := "org.polyvariant.smithy4s-bsp"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / tlJdkRelease := Some(21)
ThisBuild / tlFatalWarnings := false
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

val jsonrpclibVersion = "0.1.0"

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
    "org.typelevel" %%% "weaver-cats" % "0.11.3" % Test
  ),
)

lazy val protocol = project
  .settings(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-model" % "1.66.0"
    ),
    smithyTraitCodegenNamespace := "smithy4sbsp.meta",
    smithyTraitCodegenJavaPackage := "smithy4sbsp.meta",
    smithyTraitCodegenDependencies := Nil,
    javacOptions -= "-Xlint:all",
    Compile / doc / javacOptions ++= Seq(
      // skip "no comment" warnings in Javadoc, these Java files are just boilerplate
      "-Xdoclint:all,-missing"
    ),
  )
  .enablePlugins(SmithyTraitCodegenPlugin)
  .enablePlugins(NoPublishPlugin)

lazy val transformation = project
  .settings(
    commonSettings,
    scalaVersion := "2.12.21",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-build" % "1.66.0",
      "software.amazon.smithy" % "smithy-syntax" % "1.66.0",
      "ch.epfl.scala" % "spec-traits" % "2.2.0-M2",
      "tech.neander" % "jsonrpclib-smithy" % jsonrpclibVersion,
      "com.disneystreaming.alloy" % "alloy-core" % "0.3.36",
      "com.disneystreaming.smithy4s" % "smithy4s-protocol" % smithy4sVersion.value,
      "com.lihaoyi" %% "os-lib" % "0.11.6" % Test,
      "software.amazon.smithy" % "smithy-diff" % "1.66.0" % Test,
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
      "make-bincompat-friendly",
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
      "io.circe" %%% "circe-parser" % "0.14.15",
      "io.circe" %%% "circe-literal" % "0.14.15",
    ),
  )
  .dependsOn(codegen)

lazy val examples = project
  .settings(
    fork := true,
    commonSettings,
    libraryDependencies ++= Seq(
      "tech.neander" %%% "jsonrpclib-fs2" % jsonrpclibVersion,
      "co.fs2" %%% "fs2-io" % "3.12.2",
      "com.disneystreaming.smithy4s" %%% "smithy4s-json" % smithy4sVersion.value,
      "org.typelevel" %%% "weaver-cats" % "0.11.3" % Test,
    ),
    name := "sample-server",
  )
  .dependsOn(bsp4s)
  .enablePlugins(NoPublishPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(bsp4s, examples, codegen, transformation, protocol)
  .enablePlugins(NoPublishPlugin)
