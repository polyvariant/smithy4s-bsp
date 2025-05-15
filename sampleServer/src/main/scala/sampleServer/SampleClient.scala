package sampleServer

import bsp.OnBuildTaskFinishInput
import bsp.OnBuildTaskStartInput
import bsp.BuildClient
import bsp.BuildClientCapabilities
import bsp.BuildServer
import bsp.BuildTargetIdentifier
import bsp.DidChangeBuildTarget
import bsp.InitializeBuildParams
import bsp.LanguageId
import bsp.LogMessageParams
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

import scala.concurrent.duration.*
import cats.syntax.all.*

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
      proc
        .stdout
        .merge(proc.stderr)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .debug("[bloop] " + _)
        .compile
        .drain
        .background
    }

  def connectTo(socketFile: Path) = UnixSockets
    .forAsync[IO]
    .client(UnixSocketAddress(socketFile.toNioPath))

  def bindStreams(socket: Socket[IO], chan: FS2Channel[IO]) =
    fs2
      .Stream
      .never[IO]
      .concurrently(
        socket
          .reads
          // make sure to not use stdout in LSPs :)
          .observe(fs2.io.stdout[IO])
          .through(jsonrpclib.fs2.lsp.decodeMessages)
          .through(chan.inputOrBounce)
      )
      .concurrently(
        chan
          .output
          // make sure to not use stdout in LSPs :)
          .through(jsonrpclib.fs2.lsp.encodeMessages)
          .observe(fs2.io.stdout[IO])
          .through(socket.writes)
      )
      .compile
      .drain
      .background

  val mkBloopClient: Resource[IO, Bloop] =
    for {
      socketFile <- mkSocket
      bloopProcess <- runBloop(socketFile)
      _ <-
        (IO.consoleForIO.errorln("bloop process: " + bloopProcess) *> IO.sleep(1.second)).toResource

      socket <- connectTo(socketFile)
      chan <- FS2Channel.resource[IO]()
      handler = bspClientHandler()
      _ <- chan.withEndpoints(BSPCodecs.serverEndpoints(handler))
      _ <- bindStreams(socket, chan)
    } yield Bloop(
      BSPCodecs.clientStub(BuildServer, chan),
      BSPCodecs.clientStub(ScalaBuildServer, chan),
    )

  def bspClientHandler(): BuildClient[IO] =
    new BuildClient[IO] {
      // make sure to not use stdout in LSPs :)
      private def notify(msg: String) = IO.println(msg)

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
    bloop
      .bs
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
      .flatMap(IO.println) *>
      bloop.bs.onBuildInitialized() *>
      bloop.bs.workspaceBuildTargets().flatMap(IO.println) *>
      bloop
        .bs
        .buildTargetSources(
          SourcesParams(targets =
            List(
              BuildTargetIdentifier(
                URI("file:/Users/kubukoz/projects/smithy-playground/modules/lsp2/?id=lsp2")
              )
            )
          )
        )
        .flatMap(IO.println)

  }

}
