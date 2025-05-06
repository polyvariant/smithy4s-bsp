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
import software.amazon.smithy.model.traits.UniqueItemsTrait
import software.amazon.smithy.model.transform.ModelTransformer

class SetShapes extends ProjectionTransformer {
  def getName(): String = "set-shapes"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapShapes(
      context.getModel(),
      s =>
        s match {
          case s if s.hasTrait(bsp.traits.SetTrait.ID) =>
            val builder = s.asListShape().get().toBuilder()
            builder.removeTrait(bsp.traits.SetTrait.ID)
            builder.addTrait(new UniqueItemsTrait())
            builder.build()
          case s => s
        },
    )

}
