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

package smithy4sbsp.bsp4s

import bsp.traits.JsonNotification

import bsp.traits.JsonRequest
import jsonrpclib.Codec
import jsonrpclib.Endpoint
import smithy4s.Service
import cats.syntax.all.*
import jsonrpclib.Monadic

final case class BSPBuilder[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[_]] private (
  private val service: Service.Aux[Alg, Op],
  private val endpoints: Vector[BSPBuilder.BSPEndpoint[F]],
) {
  type Op_[I, O, E, S, F] = Op[I, O, E, S, F]

  // todo: type safety for ops from multiple services / ability to combine builders
  def withHandler[Oppy[_, _, _, _, _], I, O](
    op: smithy4s.Endpoint[Oppy, I, ?, O, ?, ?]
  )(
    f: I => F[O]
  ): BSPBuilder[Alg, Op_, F] = copy(
    service,
    endpoints =
      endpoints :+ new BSPBuilder.BSPEndpoint {
        type Op[I, O, E, S, F] = Oppy[I, O, E, S, F]
        type In = I
        type Out = O

        def endpoint: smithy4s.Endpoint[Oppy, I, ?, O, ?, ?] = op
        def impl(in: I): F[Out] = f(in)
      },
  )

  def build(
    using Monadic[F]
  ): Vector[Endpoint[F]] = {

    def handle[Op[_, _, _, _, _], I, O](e: BSPBuilder.BSPEndpoint.Aux[Op, F, I, O]) = {
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

    endpoints.map(handle(_))
  }

  extension [F[_], A](fa: F[A]) {

    private def void(
      using F: Monadic[F]
    ): F[Unit] = F.doFlatMap(fa)(_ => F.doPure(()))

  }

}

object BSPBuilder {

  def create[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[_]](
    service: Service.Aux[Alg, Op]
  ): BSPBuilder[Alg, Op, F] = new BSPBuilder(service, Vector.empty)

  trait BSPEndpoint[F[_]] {
    type Op[_, _, _, _, _]
    type In
    type Out

    def endpoint: smithy4s.Endpoint[Op, In, ?, Out, ?, ?]
    def impl(in: In): F[Out]
  }

  object BSPEndpoint {

    type Aux[Op_[_, _, _, _, _], F[_], In_, Out_] =
      BSPEndpoint[F] {
        type Op[I, O, E, S, F] = Op_[I, O, E, S, F]
        type In = In_
        type Out = Out_
      }

  }

}
