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

import bsp.BuildClient
import bsp.BuildClientCapabilities
import bsp.BuildServer
import bsp.BuildTargetIdentifier
import bsp.CompileParams
import bsp.DidChangeBuildTarget
import bsp.InitializeBuildParams
import bsp.LanguageId
import bsp.LogMessageParams
import bsp.OnBuildTaskFinishInput
import bsp.OnBuildTaskStartInput
import bsp.PrintParams
import bsp.PublishDiagnosticsParams
import bsp.ShowMessageParams
import bsp.SourcesParams
import bsp.TaskProgressParams
import bsp.URI
import bsp.scala_.ScalaBuildServer
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.Pipe
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.net.Socket
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import fs2.io.process.Process
import fs2.io.process.ProcessBuilder
import fs2.io.process.Processes
import jsonrpclib.fs2.*
import smithy4sbsp.bsp4s.BSPCodecs

object SampleClient extends IOApp.Simple {
  case class Bloop(bs: BuildServer[IO], scala: ScalaBuildServer[IO])

  val mkSocket = Files[IO].tempDirectory.map(_ / "bloop.sock")

  def runBloop(socketFile: Path): Resource[IO, Process[IO]] = Processes[IO]
    .spawn(
      ProcessBuilder(
        "bloop",
        "bsp",
        "--socket",
        socketFile.toNioPath.toString,
      )
    )
    .flatTap { proc =>
      IO.deferred[Unit].toResource.flatMap { started =>
        val waitForStart: Pipe[IO, Byte, Nothing] =
          _.through(fs2.text.utf8.decode)
            .through(fs2.text.lines)
            .find(_.contains("The server is listening for incoming connections"))
            .foreach(_ => started.complete(()).void)
            .drain

        proc
          .stdout
          .observe(waitForStart)
          .merge(proc.stderr)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .debug("[bloop] " + _)
          .compile
          .drain
          .background
        // wait for the started message before proceeding
          <* started.get.toResource
      }
    }

  def connectTo(socketFile: Path): Resource[IO, Socket[IO]] = UnixSockets
    .forAsync[IO]
    .client(UnixSocketAddress(socketFile.toNioPath))

  def bindStreams(socket: Socket[IO], chan: FS2Channel[IO]) = {
    val receive = socket
      .reads
      // make sure to not use stdout in LSPs :)
      .observe(_.through(fs2.text.utf8.decode[IO]).debug("[received from server] " + _).drain)
      .through(jsonrpclib.fs2.lsp.decodeMessages)
      .through(chan.inputOrBounce)

    val send = chan
      .output
      .through(jsonrpclib.fs2.lsp.encodeMessages)
      // make sure to not use stdout in LSPs :)
      .observe(_.through(fs2.text.utf8.decode[IO]).debug("[sending to server] " + _).drain)
      .through(socket.writes)

    fs2
      .Stream(
        receive,
        send,
      )
      .parJoinUnbounded
      .compile
      .drain
      .background
  }

  val mkBloopClient: Resource[IO, Bloop] =
    for {
      socketFile <- mkSocket
      bloopProcess <- runBloop(socketFile)
      _ <- IO.consoleForIO.errorln("bloop process: " + bloopProcess).toResource

      socket <- connectTo(socketFile)
      chan <- FS2Channel.resource[IO]()
      bloop = Bloop(
        BSPCodecs.clientStub(BuildServer, chan),
        BSPCodecs.clientStub(ScalaBuildServer, chan),
      )

      handler = bspClientHandler()
      _ <- chan.withEndpoints(BSPCodecs.serverEndpoints(handler))
      _ <- bindStreams(socket, chan)
    } yield bloop

  def bspClientHandler(): BuildClient[IO] =
    new BuildClient[IO] {
      // make sure to not use stdout in LSPs :)
      private def notify(msg: String) = IO.println(s"[in bspClientHandler] $msg")

      def onBuildLogMessage(input: LogMessageParams): IO[Unit] = notify(
        s"handling onBuildLogMessage: $input"
      )

      def onBuildPublishDiagnostics(input: PublishDiagnosticsParams): IO[Unit] = notify(
        s"handling onBuildPublishDiagnostics: $input"
      )

      def onBuildShowMessage(input: ShowMessageParams): IO[Unit] = notify(
        s"handling onBuildShowMessage: $input"
      )

      def onBuildTargetDidChange(input: DidChangeBuildTarget): IO[Unit] = notify(
        s"handling onBuildTargetDidChange: $input"
      )

      def onBuildTaskFinish(input: OnBuildTaskFinishInput): IO[Unit] = notify(
        s"handling onBuildTaskFinish: $input"
      )

      def onBuildTaskProgress(input: TaskProgressParams): IO[Unit] = notify(
        s"handling onBuildTaskProgress: $input"
      )

      def onBuildTaskStart(input: OnBuildTaskStartInput): IO[Unit] = notify(
        s"handling onBuildTaskStart: $input"
      )

      def onRunPrintStderr(input: PrintParams): IO[Unit] = notify(
        s"handling onRunPrintStderr: $input"
      )

      def onRunPrintStdout(input: PrintParams): IO[Unit] = notify(
        s"handling onRunPrintStdout: $input"
      )
    }

  def run: IO[Unit] = mkBloopClient.use { bloop =>
    val bs = bloop.bs
    bs
      .buildInitialize(
        InitializeBuildParams(
          displayName = "hello",
          version = "1.0.0",
          bspVersion = "2.0.0-M2",
          rootUri = URI("file:///Users/kubukoz/projects/smithy-playground"),
          capabilities = BuildClientCapabilities(
            languageIds = List(LanguageId("scala"), LanguageId("java"))
          ),
        )
      )
      .void
      .flatMap(IO.println) *>
      bs.onBuildInitialized() *>
      bs.workspaceBuildTargets().flatMap(IO.println) *>
      bs.buildTargetSources(
        SourcesParams(targets =
          List(
            BuildTargetIdentifier(
              URI("file:/Users/kubukoz/projects/smithy-playground/modules/lsp2/?id=lsp2")
            )
          )
        )
      ).flatMap(IO.println) *>
      bs.buildTargetCompile(
        CompileParams(
          targets = List(
            BuildTargetIdentifier(
              URI("file:/Users/kubukoz/projects/smithy-playground/modules/lsp2/?id=lsp2")
            )
          )
        )
      ).flatMap(IO.println)

  }

}
