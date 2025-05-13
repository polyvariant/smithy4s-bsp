/*
 * Copyright 2025 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sampleServer

import bsp.BuildServer

import bsp.BuildServerCapabilities
import bsp.BuildServerOperation.BuildInitialize
import bsp.BuildServerOperation.BuildShutdown
import bsp.CompileProvider
import bsp.InitializeBuildResult
import bsp.LanguageId
import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Files
import fs2.io.file.Path
import jsonrpclib.CallId
import jsonrpclib.fs2.CancelTemplate
import jsonrpclib.fs2.catsMonadic
import jsonrpclib.fs2.FS2Channel
import jsonrpclib.fs2.lsp
import bsp.BuildServerOperation.WorkspaceBuildTargets
import bsp.WorkspaceBuildTargetsResult
import bsp.BuildTarget
import bsp.BuildTargetIdentifier
import bsp.URI
import bsp.BuildTargetTag
import bsp.BuildTargetCapabilities
import bsp.BuildServerOperation.BuildTargetSources
import bsp.SourcesResult
import bsp.SourceItem
import bsp.SourceItemKind
import bsp.SourcesItem
import java.nio.file.Paths
import bsp.BuildServerOperation.BuildTargetDependencySources
import bsp.DependencySourcesResult
import bsp.BuildServerOperation.BuildTargetCompile
import bsp.CompileResult
import bsp.StatusCode
import bsp.scala_.ScalaBuildServerOperation.BuildTargetScalacOptions
import bsp.scala_.ScalacOptionsResult
import bsp.scala_.ScalaBuildServerOperation.BuildTargetScalaMainClasses
import bsp.scala_.ScalaMainClassesResult
import bsp.scala_.ScalaBuildServerOperation.BuildTargetScalaTestClasses
import bsp.scala_.ScalaTestClassesResult
import bsp.BuildServerOperation.BuildTargetCleanCache
import bsp.CleanCacheResult
import bsp.scala_.ScalacOptionsItem
import bsp.DependencySourcesItem
import bsp.scala_.ScalaPlatform
import bsp.scala_.ScalaBuildTarget
import bsp.BuildServerOperation.OnBuildExit
import smithy4sbsp.bsp4s.BSPCodecs
import bsp.scala_.ScalaBuildServer
import smithy4s.kinds.stubs.Kind1
import bsp.scala_.ScalaMainClassesParams
import bsp.scala_.ScalaTestClassesParams
import bsp.scala_.ScalacOptionsParams
import bsp.BuildClientCapabilities
import bsp.InitializeBuildParams
import bsp.InitializeBuildParamsData

object SampleServer extends IOApp.Simple {
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  val targetId = BuildTargetIdentifier(URI("proj://hello"))

  def server(log: String => IO[Unit]) = List.concat(
    BSPCodecs.serverEndpoints(
      new BuildServer.Default[IO](IO.stub) {
        override def buildInitialize(input: InitializeBuildParams): IO[InitializeBuildResult] = {
          log(s"received buildInitialize params: $input")
          IO {
            InitializeBuildResult(
              displayName = "jk-sample-server",
              "1.0.0",
              bspVersion = "2.2.0-M2",
              capabilities = BuildServerCapabilities(
                compileProvider = Some(
                  CompileProvider(languageIds = List(LanguageId("scala")))
                ),
                dependencySourcesProvider = true,
              ),
            )
          }
        }

        override def buildShutdown(
          input: Unit
        ): IO[Unit] =
          log("received a shutdown request") *>
            IO.unit

        override def buildExit(
          input: Unit
        ): IO[Unit] =
          log("received a build exit notification") *>
            IO(sys.exit(0))

        override def workspaceBuildTargets(
          input: Unit
        ): IO[WorkspaceBuildTargetsResult] =
          log("received a targets request") *>
            IO(
              WorkspaceBuildTargetsResult(
                List(
                  BuildTarget.buildTargetScalaBuildTarget(
                    id = targetId,
                    tags = List(BuildTargetTag.LIBRARY),
                    languageIds = List(LanguageId("scala")),
                    dependencies = Nil,
                    capabilities = BuildTargetCapabilities(
                      canCompile = Some(true),
                      canRun = Some(true),
                      canTest = Some(true),
                      canDebug = Some(true),
                    ),
                    displayName = Some("jk-hello"),
                    baseDirectory = Some(
                      URI(Paths.get("./").toAbsolutePath().toUri().toString())
                    ),
                    data = Some(
                      ScalaBuildTarget(
                        scalaOrganization = "org.scala-lang",
                        scalaVersion = "3.7.0-RC1",
                        scalaBinaryVersion = "3.7",
                        platform = ScalaPlatform.JVM,
                        jars = Nil,
                        jvmBuildTarget = None,
                      )
                    ),
                  )
                )
              )
            )

        override def buildTargetSources(
          input: BuildTargetIdentifier
        ): IO[SourcesResult] =
          log(s"received a sources request: $input") *>
            IO(
              SourcesResult(
                List(
                  SourcesItem(
                    target = targetId,
                    sources = List(
                      SourceItem(
                        uri = URI(Paths.get("./hello").toAbsolutePath().toUri().toString()),
                        kind = SourceItemKind.DIRECTORY,
                        generated = false,
                      )
                    ),
                  ),
                  SourcesItem(
                    target = targetId,
                    sources = List(
                      SourceItem(
                        uri = URI(Paths.get("./hello2").toAbsolutePath().toUri().toString()),
                        kind = SourceItemKind.DIRECTORY,
                        generated = false,
                      )
                    ),
                  ),
                )
              )
            )

        override def buildTargetDependencySources(
          input: BuildTargetIdentifier
        ): IO[DependencySourcesResult] =
          log(s"received dep sources params: $input") *>
            IO {
              DependencySourcesResult(
                List(
                  DependencySourcesItem(
                    target = targetId,
                    sources = Nil,
                  )
                )
              )
            }
        override def buildTargetCompile(
          input: BuildTargetIdentifier
        ): IO[CompileResult] =
          log(s"received compile params: $input") *>
            IO {
              CompileResult(
                statusCode = StatusCode.OK
              )
            }

        override def buildTargetCleanCache(
          input: BuildTargetIdentifier
        ): IO[CleanCacheResult] =
          log(s"received clean cache params: $input") *>
            IO(CleanCacheResult(cleaned = true, message = Some("cleaned cache")))
      }: BuildServer[IO]
    ),
    BSPCodecs.serverEndpoints(new ScalaBuildServer[IO] {
      override def buildTargetScalaTestClasses(
        input: ScalaTestClassesParams
      ): IO[ScalaTestClassesResult] =
        log(s"received test classes params: $input") *>
          IO {
            ScalaTestClassesResult(Nil)
          }

      override def buildTargetScalaMainClasses(
        input: ScalaMainClassesParams
      ): IO[ScalaMainClassesResult] =
        log(s"received main classes params: $input") *>
          IO {
            ScalaMainClassesResult(Nil)
          }
      override def buildTargetScalacOptions(input: ScalacOptionsParams): IO[ScalacOptionsResult] =
        log(s"received scalacOptions params: $input") *>
          IO {
            ScalacOptionsResult(Nil)
          }
    }),
  )

  def run: IO[Unit] = {
    val impl = server(msg =>
      fs2.Stream(msg + "\n").through(Files[IO].writeUtf8(Path("log.txt"))).compile.drain
    )

    FS2Channel
      .stream[IO](cancelTemplate = Some(cancelEndpoint))
      .flatMap(_.withEndpointsStream(impl.build))
      .flatMap(channel =>
        fs2
          .Stream
          .eval(IO.never) // running the server forever
          .concurrently(
            fs2
              .io
              .stdin[IO](512)
              .through(lsp.decodeMessages)
              // .broadcastThrough(_.map(_.toString).through(Files[IO].writeUtf8(Path("log.txt"))))
              .through(channel.inputOrBounce)
          )
          .concurrently(channel.output.through(lsp.encodeMessages).through(fs2.io.stdout[IO]))
      )
      .compile
      .drain
      .guarantee(IO.consoleForIO.errorln("Terminating server"))
  }

}
