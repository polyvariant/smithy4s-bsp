import bsp.traits.JsonNotification

import bsp.traits.JsonRequest
import cats.effect.IO
import jsonrpclib.Codec
import jsonrpclib.Endpoint
import jsonrpclib.fs2.*
import smithy4s.Service

final case class BSPBuilder[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]] private (
  private val service: Service.Aux[Alg, Op],
  private val endpoints: Vector[BSPBuilder.BSPEndpoint],
) {
  type Op_[I, O, E, S, F] = Op[I, O, E, S, F]

  // todo: type safety for ops from multiple services / ability to combine builders
  def withHandler[Oppy[_, _, _, _, _], I, O](
    op: smithy4s.Endpoint[Oppy, I, ?, O, ?, ?]
  )(
    f: I => IO[O]
  ): BSPBuilder[Alg, Op_] = copy(
    service,
    endpoints =
      endpoints :+ new BSPBuilder.BSPEndpoint {
        type Op[I, O, E, S, F] = Oppy[I, O, E, S, F]
        type In = I
        type Out = O

        def endpoint: smithy4s.Endpoint[Oppy, I, ?, O, ?, ?] = op
        def impl(in: I): IO[Out] = f(in)
      },
  )

  def bind(chan: FS2Channel[IO]): fs2.Stream[IO, FS2Channel[IO]] = {

    def handle[Op[_, _, _, _, _], I, O](e: BSPBuilder.BSPEndpoint.Aux[Op, I, O]) = {
      given Codec[I] = BSPCodecs.codecFor[I](
        using e.endpoint.input
      )
      given Codec[O] = BSPCodecs.codecFor[O](
        using e.endpoint.output
      )

      e.endpoint.hints match {
        case JsonRequest.hint(req) => Endpoint(req.value).simple(e.impl)
        case JsonNotification.hint(noti) =>
          Endpoint(noti.value).notification(e.impl.andThen(_.void))
        case _ =>
          sys.error(s"invalid endpoint ${e.endpoint.id}: no JsonRequest/JsonNotification present")
      }
    }

    chan.withEndpointsStream(endpoints.map(handle(_)))
  }

}

object BSPBuilder {

  def create[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    service: Service.Aux[Alg, Op]
  ): BSPBuilder[Alg, Op] = new BSPBuilder(service, Vector.empty)

  trait BSPEndpoint {
    type Op[_, _, _, _, _]
    type In
    type Out

    def endpoint: smithy4s.Endpoint[Op, In, ?, Out, ?, ?]
    def impl(in: In): IO[Out]
  }

  object BSPEndpoint {

    type Aux[Op_[_, _, _, _, _], In_, Out_] =
      BSPEndpoint {
        type Op[I, O, E, S, F] = Op_[I, O, E, S, F]
        type In = In_
        type Out = Out_
      }

  }

}
