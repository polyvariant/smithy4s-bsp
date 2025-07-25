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

import common.TransformationUtils.*
import smithy4s.meta.AdtTrait
import smithy4s.meta.BincompatFriendlyTrait
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.ErrorTrait
import software.amazon.smithy.model.traits.MixinTrait

import scala.collection.JavaConverters.*

class MakeBincompatFriendly extends ProjectionTransformer {
  def getName(): String = "make-bincompat-friendly"

  def transform(context: TransformContext): Model = {

    val inputsOrOutputs =
      context
        .getModel()
        .getOperationShapes()
        .asScala
        .flatMap { op =>
          Set(op.getInputShape(), op.getOutputShape()).map(context.getModel().expectShape)
        }
        .toSet

    val adtTargets =
      context
        .getModel()
        .getShapesWithTrait(classOf[AdtTrait])
        .asScala
        .flatMap(_.members().asScala.map(_.getTarget()))
        .map(context.getModel().expectShape(_))
        .toSet

    val illegalShapes = (inputsOrOutputs ++ adtTargets).map(_.getId())

    val result = context.getModel().mapSomeShapes {
      case s
          if s.getId().getNamespace().startsWith("bsp")
            && Set(ShapeType.STRUCTURE, ShapeType.UNION, ShapeType.ENUM, ShapeType.INT_ENUM)
              .contains(s.getType()) &&
            !s.hasTrait(classOf[AdtTrait]) &&
            !s.hasTrait(classOf[ErrorTrait]) &&
            !s.hasTrait(classOf[MixinTrait]) &&
            !illegalShapes(s.getId()) =>

        val b: AbstractShapeBuilder[? <: AbstractShapeBuilder[_, _], Shape] = Shape.shapeToBuilder(
          s
        )

        b.addTrait(BincompatFriendlyTrait.builder().build())
        b.build()
    }

    result
  }

}
