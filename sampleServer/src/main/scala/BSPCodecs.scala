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
        fa.hints match {
          case RpcPayload.hint(_) =>
            // we need to flatten this, meaning it's a struct of just 1 member and we need to encode it
            // as if it was just the member.
            fa match {
              case struu: StructSchema[b] =>
                require(struu.fields.sizeIs == 1)
                val field = struu.fields.head
                field.schema.biject[b](f => struu.make(Vector(f)))(field.get)
              case _ =>
                sys.error("Unexpected non-struct schema used with RpcPayload: " + fa.shapeId)
            }
          case _ => fa
        }
    }

}
