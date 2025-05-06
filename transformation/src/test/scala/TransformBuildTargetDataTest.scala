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

import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.diff.ModelDiff
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer
import weaver.*
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.loader.ModelAssembler
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import java.nio.file.Paths

object TransformBuildTargetDataTest extends FunSuite {
  test("Sample transformation of data") {

    val result = new TransformBuildTargetData().transform(
      TransformContext
        .builder()
        .model(loadModel(os.sub / "smithy" / "sampleDataTraits" / "input.smithy"))
        .build()
    )

    val expected = loadModel(
      os.sub / "smithy" / "sampleDataTraits" / "expected.smithy"
    )

    val diff =
      ModelDiff
        .builder()
        .oldModel(expected)
        .newModel(result)
        .classLoader(this.getClass().getClassLoader())
        .compare()
        .getDiffEvents()
        .asScala
        .toList

    if (diff.nonEmpty) {
      val tmp = os.temp(
        SmithyIdlModelSerializer
          .builder()
          .build()
          .serialize(result)
          .get(Paths.get("sample.smithy")),
        suffix = "sample.smithy",
      )
      println(s"wrote to $tmp")
    }

    assert(diff.isEmpty, diff.map(_.toString()).mkString("\n"))
  }

  private def loadModel(resources: os.SubPath*): Model = {
    val assembler = Model
      .assembler(this.getClass().getClassLoader())
      .discoverModels(this.getClass().getClassLoader())
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)

    resources.foreach { res =>
      assembler.addImport(this.getClass().getResource(res.toString()))
    }

    ModelTransformer
      .create
      .filterShapes(
        assembler
          .assemble()
          .unwrap(),
        s =>
          s.getId().getNamespace().startsWith("traits") ||
            !s.getId().getNamespace().startsWith("bsp"),
      )

  }

}
