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

import scala.collection.JavaConverters.*
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import java.nio.file.Files
import java.nio.file.Paths

class Serialize extends ProjectionTransformer {
  def getName(): String = "serialize"

  def transform(context: TransformContext): Model = {
    val model = context.getModel()

    SmithyIdlModelSerializer
      .builder()
      .basePath(Paths.get("transformed"))
      .shapeFilter(s => s.getId().getNamespace().startsWith("bsp"))
      .build()
      .serialize(model)
      .asScala
      .toMap
      .foreach { case (path, content) =>
        Files.createDirectories(path.getParent())

        Files.writeString(path, content)
      }

    model
  }

}
