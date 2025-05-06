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
