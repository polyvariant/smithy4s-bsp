package bsp4s.proxy

import alloy.SimpleRestJson
import bsp.BuildClient
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import fs2.io.process.Processes
import jsonrpclib.JsonNotification
import jsonrpclib.JsonRequest
import jsonrpclib.fs2.*
import org.http4s.ember.server.EmberServerBuilder
import smithy.api.Http
import smithy.api.NonEmptyString
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.kinds.PolyFunction5
import smithy4sbsp.bsp4s.BSPCodecs

import concurrent.duration.*

// HTTP proxy over bsp
// currently hardcoded to bloop
object BSPProxy extends IOApp.Simple {

  def run: IO[Unit] =
    mkBloopClient
      .flatMap { channel =>
        SimpleRestJsonBuilder
          .routes(channel)(
            using service =
              bsp
                .BuildServer
                .toBuilder
                .mapHints(h => h.add(SimpleRestJson()))
                .mapEndpointEach(
                  new PolyFunction5[bsp.BuildServer.Endpoint, bsp.BuildServer.Endpoint] {
                    def apply[I, E, O, SI, SO](
                      fa: bsp.BuildServer.Endpoint[I, E, O, SI, SO]
                    ): bsp.BuildServer.Endpoint[I, E, O, SI, SO] = fa.mapSchema(
                      _.mapHints(h =>
                        h.add(
                          Http(
                            method = NonEmptyString("POST"),
                            uri = NonEmptyString("/bsp" + fa.hints.match {
                              case JsonNotification.hint(not) => show"/notification/${not.value}"
                              case JsonRequest.hint(req)      => show"/request/${req.value}"
                            }),
                          )
                        )
                      )
                    )
                  }
                )
                .build
          )
          .resource
          .flatMap { routes =>
            EmberServerBuilder.default[IO].withHttpApp(routes.orNotFound).build
          }
      }
      .evalMap(srv => IO.println(s"Server running at: ${srv.baseUri}"))
      .useForever

  private def mkBloopClient: Resource[IO, bsp.BuildServer[IO]] = Files[IO]
    .tempDirectory
    .flatMap { temp =>
      Processes[IO]
        .spawn(
          fs2
            .io
            .process
            .ProcessBuilder(
              "bloop",
              "bsp",
              "--socket",
              (temp / "bloop.sock").toNioPath.toString,
            )
        )
        .evalTap(p =>
          IO.consoleForIO.errorln("socket: " + (temp / "bloop.sock")) *> IO.sleep(1.second)
        )
        .flatMap { proc =>
          UnixSockets
            .forAsync[IO]
            .client(UnixSocketAddress((temp / "bloop.sock").toNioPath))
            .flatMap { socket =>
              jsonrpclib.fs2.FS2Channel.resource[IO]().flatMap { chan =>
                // we use the sock

                fs2
                  .Stream
                  .never[IO]
                  .concurrently(
                    socket
                      .reads
                      .observe(_.through(fs2.text.utf8.decode[IO]).evalMap(IO.println(_)).drain)
                      .through(jsonrpclib.fs2.lsp.decodeMessages)
                      .through(chan.inputOrBounce)
                  )
                  .concurrently(
                    chan
                      .output
                      .through(jsonrpclib.fs2.lsp.encodeMessages)
                      .observe(_.through(fs2.text.utf8.decode[IO]).evalMap(IO.println(_)).drain)
                      .through(socket.writes)
                  )
                  .compile
                  .drain
                  .background
                  .flatMap { _ =>
                    val extraEndpoints = BSPCodecs.serverEndpoints(
                      bsp
                        .BuildClient
                        .impl(new bsp.BuildClient.FunctorEndpointCompiler[IO] {
                          def apply[A0, A1, A2, A3, A4](
                            fa: BuildClient.Endpoint[A0, A1, A2, A3, A4]
                          ): A0 => IO[A2] =
                            in =>
                              IO {
                                System
                                  .err
                                  .println(s"${fa.name}: received: " + in)
                                  .asInstanceOf[A2]
                              }
                        })
                    )

                    chan.withEndpoints(extraEndpoints).map(_ => chan)
                  }
              }

            }
        }
        .map { channel =>
          BSPCodecs.clientStub(bsp.BuildServer, channel)
        }
    }

}
