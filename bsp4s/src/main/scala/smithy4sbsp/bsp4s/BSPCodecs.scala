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

import alloy.Discriminated
import jsonrpclib.Channel
import jsonrpclib.Monadic
import jsonrpclib.smithy4sinterop.ClientStub
import jsonrpclib.smithy4sinterop.ServerEndpoints
import smithy.api.JsonName
import smithy4s.Document
import smithy4s.Document.DObject
import smithy4s.Endpoint
import smithy4s.Refinement
import smithy4s.Service
import smithy4s.ShapeTag
import smithy4s.kinds.FunctorAlgebra
import smithy4s.schema.OperationSchema
import smithy4s.schema.Schema
import smithy4s.schema.Schema.StructSchema
import smithy4s.schema.Schema.UnionSchema
import smithy4s.~>
import smithy4sbsp.meta.DataDefault

import util.chaining.*
import jsonrpclib.JsonRpcPayload
import smithy4s.UnsupportedProtocolError

object BSPCodecs {

  def clientStub[Alg[_[_, _, _, _, _]], F[_]: Monadic](
    service: Service[Alg],
    chan: Channel[F],
  ): Either[UnsupportedProtocolError, service.Impl[F]] = ClientStub(
    bspServiceTransformations(service),
    chan,
  )

  def serverEndpoints[Alg[_[_, _, _, _, _]], F[_]: Monadic](
    impl: FunctorAlgebra[Alg, F]
  )(
    using service: Service[Alg]
  ): Either[UnsupportedProtocolError, List[jsonrpclib.Endpoint[F]]] =
    ServerEndpoints[Alg, F](impl)(
      using bspServiceTransformations(service)
    )

  private[bsp4s] def bspServiceTransformations[Alg[_[_, _, _, _, _]]]
    : Service[Alg] => Service[Alg] =
    _.toBuilder
      .mapEndpointEach(
        Endpoint.mapSchema(
          OperationSchema
            .mapInputK(bspTransformations)
            .andThen(OperationSchema.mapOutputK(bspTransformations))
        )
      )
      .build

  // todo: reconcile this with the one from jsonrpclib.
  // we can't atm, because this one has to run before addDataDefault. Otherwise, if we run addDataDefault,
  // the struct schema is gone (it gets turned into a Document Decoder wrapped in a refinement).
  private val flattenRpcPayload: Schema ~> Schema =
    new (Schema ~> Schema) {
      def apply[A0](fa: Schema[A0]): Schema[A0] =
        fa match {
          case struct: StructSchema[b] =>
            struct
              .fields
              .collectFirst {
                case field if field.hints.has[JsonRpcPayload] =>
                  field.schema.biject[b](f => struct.make(Vector(f)))(field.get)
              }
              .getOrElse(fa)
          case _ => fa
        }
    }

  // war crimes inside
  private val addDataDefault: Schema ~> Schema =
    new (Schema ~> Schema) {
      def apply[A0](fa: Schema[A0]): Schema[A0] =
        fa match {
          case UnionSchema(shapeId, hints, alternatives, ordinal)
              if alternatives.exists(_.hints.has[DataDefault]) =>
            val altWithDefault =
              alternatives
                .find(_.hints.has[DataDefault])
                .get // we know it's there because of the guard

            val discriminatorTag = hints
              .get[Discriminated]
              .getOrElse(
                sys.error(
                  "Discriminated is not set on the union. This is a bug in the code generation."
                )
              )

            val altJsonName = altWithDefault
              .hints
              .get[JsonName]
              .map(_.value)
              .getOrElse(altWithDefault.label)

            val discriminatorValue = Document.fromString(altJsonName)

            def decode(doc: Map[String, Document]) =
              doc
                .get(discriminatorTag.value)
                .match {
                  case Some(_) =>
                    // discriminator is there, we decode normally
                    doc
                  case None =>
                    // no discriminator. We have to add the "default" one so that the union decoder cam pick it up
                    doc + (discriminatorTag.value -> discriminatorValue)
                }
                .pipe(Document.obj(_))
                .decode(
                  using Document.Decoder.fromSchema(fa)
                )
                .left
                .map(_.getMessage)

            def encode(b: A0): Map[String, Document] =
              Document.Encoder.fromSchema(fa).encode(b) match {
                case DObject(value) if value(discriminatorTag.value) == discriminatorValue =>
                  value - discriminatorTag.value
                case other @ DObject(keys) => keys
                case other                 => sys.error("Expected DObject but got: " + other)
              }

            val decodeRefin =
              new Refinement[Map[String, Document], A0] {
                type Constraint = Unit
                val tag: ShapeTag[Unit] =
                  new ShapeTag[Unit] {
                    def id: smithy4s.ShapeId = smithy4s.ShapeId("smithy.api", "Unit")
                    def schema: Schema[Unit] = Schema.unit
                  }

                def apply(a: Map[String, Document]): Either[String, A0] = decode(a)
                def from(b: A0): Map[String, Document] = encode(b)

                def constraint: Constraint = ()
                def unsafe(a: Map[String, Document]): A0 = apply(a).fold(sys.error, identity)
              }

            Schema
              .map(Schema.string, Schema.document)
              .withId(shapeId)
              .refined[A0]
              .apply(decodeRefin)

          case _ => fa
        }
    }

  private[bsp4s] val bspTransformations: Schema ~> Schema = Schema
    .transformTransitivelyK(flattenRpcPayload)
    .andThen(Schema.transformTransitivelyK(addDataDefault))

}
