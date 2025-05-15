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

import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer
import bsp.traits.JsonNotificationTrait
import bsp.traits.JsonRequestTrait
import bsp.traits.JsonRPCTrait
import java.util.function.BiFunction
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.shapes.Shape

class TransformJsonRpcTraits extends ProjectionTransformer {
  def getName(): String = "transform-jsonrpclib-traits"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapTraits(
      context.getModel(),
      List[BiFunction[Shape, Trait, Trait]](
        {
          case (_, _: JsonRPCTrait) => jsonrpclib.JsonRPCTrait.builder().build()
          case (_, trt)             => trt
        },
        {
          case (_, trt: JsonNotificationTrait) =>
            new jsonrpclib.JsonNotificationTrait(trt.getValue())
          case (_, trt) => trt
        },
        {
          case (_, trt: JsonRequestTrait) => new jsonrpclib.JsonRequestTrait(trt.getValue())
          case (_, trt)                   => trt
        },
      ).asJava,
    )

}
