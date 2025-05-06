package smithy4sbsp.bsp4s
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

import BSPCodecs.codecFor
import bsp.BuildTarget
import bsp.BuildTargetCapabilities
import bsp.BuildTargetIdentifier
import bsp.BuildTargetTag
import bsp.LanguageId
import bsp.URI
import bsp.WorkspaceBuildTargetsResult
import bsp.scala_.ScalaBuildTarget
import bsp.scala_.ScalaBuildTargetData
import bsp.scala_.ScalaPlatform
import cats.effect.IO
import smithy4s.Document
import smithy4s.json.Json
import weaver.*

import java.nio.file.Paths
import smithy4s.schema.Schema
import BuildTargetTest.BuildTargetTestInput
import bsp.TestParams
import bsp.scala_.ScalaTestParams

object BSPCodecsTest extends FunSuite {
  test("BuildTargetTestInput") {
    val input = BuildTargetTestInput(
      data = TestParams.scala_test(
        ScalaTestParams(targets = Nil)
      )
    )

    roundtripTest(
      input,
      Document.obj(
        "dataKind" -> Document.fromString("scala-test"),
        "targets" -> Document.array(),
      ),
    )
  }
  test("WorkspaceBuildTargetsResult") {
    val targetId = BuildTargetIdentifier(URI("proj://hello"))

    val input = WorkspaceBuildTargetsResult(
      List(
        BuildTarget.scala(
          ScalaBuildTarget(
            id = targetId,
            tags = List(BuildTargetTag.LIBRARY),
            languageIds = List(LanguageId("scala")),
            dependencies = Nil,
            capabilities = BuildTargetCapabilities(
              canCompile = Some(true),
              canRun = Some(true),
              canTest = Some(true),
              canDebug = Some(true),
            ),
            displayName = Some("jk-hello"),
            baseDirectory = Some(
              URI(Paths.get("./").toAbsolutePath().toUri().toString())
            ),
            data = Some(
              ScalaBuildTargetData(
                scalaOrganization = "org.scala-lang",
                scalaVersion = "3.7.0-RC1",
                scalaBinaryVersion = "3.7",
                platform = ScalaPlatform.JVM,
                jars = Nil,
                jvmBuildTarget = None,
              )
            ),
          )
        )
      )
    )

    val expected = Document.obj(
      "targets" -> Document.array(
        Document.obj(
          "dataKind" -> Document.fromString("scala"),
          "tags" -> Document.array(
            Document.fromString("library")
          ),
          "data" -> Document.obj(
            "jars" -> Document.array(),
            "scalaBinaryVersion" -> Document.fromString("3.7"),
            "scalaVersion" -> Document.fromString("3.7.0-RC1"),
            "platform" -> Document.fromInt(1),
            "scalaOrganization" -> Document.fromString("org.scala-lang"),
          ),
          "languageIds" -> Document.array(
            Document.fromString("scala")
          ),
          "id" -> Document.obj(
            "uri" -> Document.fromString("proj://hello")
          ),
          "baseDirectory" -> Document.fromString(
            "file:///Users/kubukoz/projects/smithy4s-bsp/./"
          ),
          "dependencies" -> Document.array(),
          "displayName" -> Document.fromString("jk-hello"),
          "capabilities" -> Document.obj(
            "canCompile" -> Document.fromBoolean(true),
            "canTest" -> Document.fromBoolean(true),
            "canRun" -> Document.fromBoolean(true),
            "canDebug" -> Document.fromBoolean(true),
          ),
        )
      )
    )

    roundtripTest(input, expected)
  }

  private def roundtripTest[A: Schema](
    a: A,
    encoded: Document,
  )(
    using SourceLocation
  ): Expectations = {
    val c = codecFor[A]

    val encoded2 = c.encode(a)

    val result = Json.readDocument(encoded2.stripNull.get.array).toTry.get

    val decoded = c.decode(Some(encoded2))

    assert.same(result, encoded) &&
    assert.same(Right(a), decoded)
  }

}
