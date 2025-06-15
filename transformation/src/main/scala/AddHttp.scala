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
import common.TransformationUtils.ModelOps
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ServiceShape
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.traits.HttpTrait
import jsonrpclib.JsonNotificationTrait
import software.amazon.smithy.model.pattern.UriPattern
import alloy.SimpleRestJsonTrait
import jsonrpclib.JsonRequestTrait

class AddHttp extends ProjectionTransformer {
  def getName(): String = "add-http"

  def transform(context: TransformContext): Model = {

    val opsUsedInBuildServer =
      context
        .getModel()
        .expectShape(ShapeId.from("bsp#BuildServer"), classOf[ServiceShape])
        .getAllOperations()
        .asScala
        .toSet
    context.getModel().mapSomeShapes {
      case s if s.getId() == ShapeId.from("bsp#BuildServer") =>
        s.asServiceShape().get().toBuilder().addTrait(new SimpleRestJsonTrait()).build()
      case s if opsUsedInBuildServer.contains(s.getId()) =>
        s.asOperationShape()
          .get()
          .toBuilder()
          .addTrait(
            HttpTrait
              .builder()
              .method("POST")
              .uri(
                UriPattern.parse {
                  s.getTrait(classOf[JsonNotificationTrait])
                    .map[String]("/" + _.getValue())
                    .or(() =>
                      s.getTrait(classOf[JsonRequestTrait])
                        .map[String]("/" + _.getValue())
                    )
                    .orElseThrow(() =>
                      new Exception(
                        s"Operation ${s.getId()} does not have a JsonNotificationTrait or JsonRequestTrait. It has: ${s.getAllTraits().asScala.toMap}"
                      )
                    )
                }
              )
              .build()
          )
          .build()
    }
  }

}
