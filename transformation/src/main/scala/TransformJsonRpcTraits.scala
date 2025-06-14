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

import bsp.traits.JsonNotificationTrait
import bsp.traits.JsonRPCTrait
import bsp.traits.JsonRequestTrait
import common.TransformationUtils.*
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model

class TransformJsonRpcTraits extends ProjectionTransformer {
  def getName(): String = "transform-jsonrpclib-traits"

  def transform(context: TransformContext): Model = context
    .getModel()
    .mapSomeTraits(
      { case (_, _: JsonRPCTrait) => jsonrpclib.JsonRpcTrait.builder().build() },
      { case (_, trt: JsonNotificationTrait) =>
        new jsonrpclib.JsonRpcNotificationTrait(trt.getValue())
      },
      { case (_, trt: JsonRequestTrait) => new jsonrpclib.JsonRpcRequestTrait(trt.getValue()) },
    )

}
