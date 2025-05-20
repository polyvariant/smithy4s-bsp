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

import alloy.OpenEnumTrait
import bsp.traits.EnumKindTrait.EnumKind.CLOSED
import bsp.traits.EnumKindTrait.EnumKind.OPEN
import common.TransformationUtils.*
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.Shape

class OpenEnums extends ProjectionTransformer {
  def getName(): String = "open-enums"

  def transform(context: TransformContext): Model = context.getModel().mapSomeShapes {
    case s if s.hasTrait(bsp.traits.EnumKindTrait.ID) =>
      val builder = Shape.shapeToBuilder(s): AbstractShapeBuilder[_, _]
      builder.removeTrait(bsp.traits.EnumKindTrait.ID)

      s.expectTrait(classOf[bsp.traits.EnumKindTrait]).getEnumKind() match {
        case OPEN   => builder.addTrait(new OpenEnumTrait())
        case CLOSED => () // do nothing, we remove the trait anyway
      }

      builder.build()
  }

}
