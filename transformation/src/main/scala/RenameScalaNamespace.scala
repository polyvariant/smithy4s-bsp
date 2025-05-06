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
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.transform.ModelTransformer

import scala.collection.JavaConverters.*

class RenameScalaNamespace extends ProjectionTransformer {
  def getName(): String = "rename-scala-namespace"

  val renames = Map(
    "bsp.scala" -> "bsp.scala_",
    "bsp.java" -> "bsp.java_",
    "traits" -> "bsp.traits",
  )

  object Renamed {

    def unapply(id: ShapeId): Option[ShapeId] = renames.collectFirst {
      case (prefix, prefixRename) if id.getNamespace().startsWith(prefix) =>
        id.withNamespace(id.getNamespace().replace(prefix, prefixRename))
    }

  }

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .renameShapes(
      context.getModel(),
      context
        .getModel()
        .getShapeIds()
        .asScala
        .collect { case id @ Renamed(renamed) => id -> renamed }
        .toMap
        .asJava,
    )

}
