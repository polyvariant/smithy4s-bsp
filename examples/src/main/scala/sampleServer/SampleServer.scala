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
import bsp.BuildTarget
import bsp.BuildTargetCapabilities
import bsp.BuildTargetIdentifier
import bsp.BuildTargetTag
import bsp.CleanCacheParams
import bsp.CleanCacheResult
import bsp.CompileParams
import bsp.CompileProvider
import bsp.CompileResult
import bsp.DependencySourcesItem
import bsp.DependencySourcesParams
import bsp.DependencySourcesResult
import bsp.InitializeBuildParams
import bsp.InitializeBuildResult
import bsp.LanguageId
import bsp.SourceItem
import bsp.SourceItemKind
import bsp.SourcesItem
import bsp.SourcesParams
import bsp.SourcesResult
import bsp.StatusCode
import bsp.URI
import bsp.WorkspaceBuildTargetsResult
import bsp.scala_.ScalaBuildServer
import bsp.scala_.ScalaBuildTarget
import bsp.scala_.ScalaMainClassesParams
import bsp.scala_.ScalaMainClassesResult
import bsp.scala_.ScalaPlatform
import bsp.scala_.ScalaTestClassesParams
import bsp.scala_.ScalaTestClassesResult
import bsp.scala_.ScalacOptionsParams
import bsp.scala_.ScalacOptionsResult
import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Files
import fs2.io.file.Flags
import fs2.io.file.Path
import jsonrpclib.CallId
import jsonrpclib.fs2.CancelTemplate
import jsonrpclib.fs2.FS2Channel
import jsonrpclib.fs2.catsMonadic
import jsonrpclib.fs2.lsp
import smithy4s.kinds.stubs.Kind1
import smithy4sbsp.bsp4s.BSPCodecs

import java.nio.file.Paths

object SampleServer extends IOApp.Simple {
  val cancelEndpoint = CancelTemplate.make[CallId]("$/cancel", identity, identity)

  val targetId = BuildTargetIdentifier(URI("proj://hello"))

  def server(log: String => IO[Unit]) = List.concat(
    BSPCodecs
      .serverEndpoints(
        new BuildServer.Default[IO](IO.stub) {
          override def buildInitialize(input: InitializeBuildParams): IO[InitializeBuildResult] =
            log(s"received buildInitialize params: $input") *>
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

          override def buildShutdown(): IO[Unit] =
            log("received a shutdown request") *>
              IO.unit

          override def onBuildExit(): IO[Unit] =
            log("received a build exit notification") *>
              IO(sys.exit(0))

          override def workspaceBuildTargets(
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
                      data = ScalaBuildTarget(
                        scalaOrganization = "org.scala-lang",
                        scalaVersion = "3.7.0-RC1",
                        scalaBinaryVersion = "3.7",
                        platform = ScalaPlatform.JVM,
                        jars = Nil,
                        jvmBuildTarget = None,
                      ),
                    )
                  )
                )
              )

          override def buildTargetSources(
            input: SourcesParams
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
            input: DependencySourcesParams
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
            input: CompileParams
          ): IO[CompileResult] =
            log(s"received compile params: $input") *>
              IO {
                CompileResult(
                  statusCode = StatusCode.OK
                )
              }

          override def buildTargetCleanCache(
            input: CleanCacheParams
          ): IO[CleanCacheResult] =
            log(s"received clean cache params: $input") *>
              IO(CleanCacheResult(cleaned = true, message = Some("cleaned cache")))
        }: BuildServer[IO]
      )
      .toTry
      .get,
    BSPCodecs
      .serverEndpoints(new ScalaBuildServer[IO] {
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
      })
      .toTry
      .get,
  )

  def run: IO[Unit] = {
    val endpoints = server(msg =>
      fs2
        .Stream(msg + "\n")
        .through(Files[IO].writeUtf8(Path("log.txt"), Flags.Append))
        .compile
        .drain
    )

    FS2Channel
      .stream[IO](cancelTemplate = Some(cancelEndpoint))
      .flatMap(_.withEndpointsStream(endpoints))
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
