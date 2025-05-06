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

class TransformJsonRpcTraits extends ProjectionTransformer {
  def getName(): String = "transform-jsonrpclib-traits"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapShapes(
      context.getModel(),
      s =>
        s match {
          case s if s.hasTrait(JsonRPCTrait.ID) =>
            val builder = s.asServiceShape.get.toBuilder()
            builder.removeTrait(JsonRPCTrait.ID)
            builder.addTrait(jsonrpclib.JsonRPCTrait.builder().build())
            builder.build()

          case s if s.hasTrait(JsonNotificationTrait.ID) =>
            val builder = s.asOperationShape.get.toBuilder()
            builder.removeTrait(JsonNotificationTrait.ID)
            builder.addTrait(
              new jsonrpclib.JsonNotificationTrait.Provider().createTrait(
                jsonrpclib.JsonNotificationTrait.ID,
                s.getAllTraits().get(JsonNotificationTrait.ID).toNode(),
              )
            )
            builder.build()

          case s if s.hasTrait(JsonRequestTrait.ID) =>
            val builder = s.asOperationShape.get.toBuilder()
            builder.removeTrait(JsonRequestTrait.ID)
            builder.addTrait(
              new jsonrpclib.JsonRequestTrait.Provider().createTrait(
                jsonrpclib.JsonRequestTrait.ID,
                s.getAllTraits().get(JsonRequestTrait.ID).toNode(),
              )
            )
            builder.build()
          case s => s
        },
    )

}
