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
import software.amazon.smithy.model.traits.DynamicTrait
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.node.Node
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
            // generate these traits Kasper!
            builder.addTrait(
              new DynamicTrait(ShapeId.from("jsonrpclib#jsonRPC"), Node.objectNode())
            )
            builder.build()

          case s if s.hasTrait(JsonNotificationTrait.ID) =>
            val builder = s.asOperationShape.get.toBuilder()
            builder.removeTrait(JsonNotificationTrait.ID)
            // generate these traits Kasper!
            builder.addTrait(
              new DynamicTrait(
                ShapeId.from("jsonrpclib#jsonNotification"),
                s.getAllTraits().get(JsonNotificationTrait.ID).toNode(),
              )
            )
            builder.build()

          case s if s.hasTrait(JsonRequestTrait.ID) =>
            val builder = s.asOperationShape.get.toBuilder()
            builder.removeTrait(JsonRequestTrait.ID)
            // generate these traits Kasper!
            builder.addTrait(
              new DynamicTrait(
                ShapeId.from("jsonrpclib#jsonRequest"),
                s.getAllTraits().get(JsonRequestTrait.ID).toNode(),
              )
            )
            builder.build()
          case s => s
        },
    )

}
