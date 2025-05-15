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

import bsp.traits.SetTrait
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.traits.UniqueItemsTrait
import software.amazon.smithy.model.transform.ModelTransformer

import java.util.function.BiFunction

class SetShapes extends ProjectionTransformer {
  def getName(): String = "set-shapes"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapTraits(
      context.getModel(),
      {
        case (_, _: SetTrait) => new UniqueItemsTrait()
        case (_, trt)         => trt
      }: BiFunction[Shape, Trait, Trait],
    )

}
