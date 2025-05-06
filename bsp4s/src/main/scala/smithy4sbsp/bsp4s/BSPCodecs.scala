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

import cats.syntax.all.*

import jsonrpclib.Codec
import jsonrpclib.Payload
import jsonrpclib.ProtocolError
import smithy4s.Blob
import smithy4s.~>
import smithy4s.json.Json
import smithy4s.schema.Schema

import util.chaining.*
import smithy4sbsp.meta.RpcPayload
import smithy4s.schema.Schema.StructSchema
import smithy4s.Service
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.fs2.FS2Channel
import cats.effect.kernel.Async
import smithy4s.Endpoint
import smithy4s.schema.OperationSchema

object BSPCodecs {

  def codecFor[A: Schema]: Codec[A] =
    new {
      val schema = Schema[A].transformTransitivelyK(bspTransformations)

      private val decoder = Json.payloadCodecs.decoders.fromSchema[A](schema)

      private val encoder = Json.payloadCodecs.encoders.fromSchema[A](schema)

      def decode(payload: Option[Payload]): Either[ProtocolError, A] = payload
        .flatMap(_.stripNull)
        .map(_.array)
        .fold(Blob.empty)(Blob.apply(_))
        .pipe { blob =>
          decoder
            .decode(blob)
            .leftMap(pe => ProtocolError.ParseError(pe.toString()))
        }

      def encode(a: A): Payload = Payload.Data(encoder.encode(a).toArrayUnsafe)
    }

  def clientStub[Alg[_[_, _, _, _, _]], F[_]: Async](service: Service[Alg], chan: FS2Channel[F])
    : F[service.Impl[F]] = ClientStub(bspServiceTransformations(service), chan)

  // todo: probably should be private
  def bspServiceTransformations[Alg[_[_, _, _, _, _]]]: Service[Alg] => Service[Alg] =
    _.toBuilder
      .mapEndpointEach(
        Endpoint.mapSchema(
          OperationSchema
            .mapInputK(
              Schema.transformTransitivelyK(bspTransformations)
            )
            .andThen(
              OperationSchema.mapOutputK(
                Schema.transformTransitivelyK(bspTransformations)
              )
            )
        )
      )
      .build

  private def bspTransformations: Schema ~> Schema =
    new (Schema ~> Schema) {
      def apply[A0](fa: Schema[A0]): Schema[A0] =
        fa match {
          case struct: StructSchema[b] =>
            struct
              .fields
              .collectFirst {
                case field if field.hints.has[RpcPayload] =>
                  field.schema.biject[b](f => struct.make(Vector(f)))(field.get)
              }
              .getOrElse(fa)
          case _ => fa
        }
    }

}
