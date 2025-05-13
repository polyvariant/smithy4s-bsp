package jsonrpclib

import smithy4s.schema.Schema
import io.circe.Codec

object Hack {
  def schemaToCodec[A: Schema]: Codec[A] = smithy4sinterop.CirceJson.fromSchema[A]
}
