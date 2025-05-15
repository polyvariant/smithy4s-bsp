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

import bsp.BuildTargetTestInput
import bsp.BuildTarget
import bsp.BuildTargetCapabilities
import bsp.BuildTargetIdentifier
import bsp.BuildTargetTag
import bsp.LanguageId
import bsp.TestParams
import bsp.URI
import bsp.WorkspaceBuildTargetsResult
import bsp.jvm.JvmBuildTarget
import bsp.scala_.ScalaBuildTarget
import bsp.scala_.ScalaPlatform
import cats.effect.IO
import io.circe.*
import io.circe.Codec
import io.circe.literal.*
import smithy4s.schema.Schema
import weaver.*

import java.nio.file.Paths
import scala.annotation.nowarn
import jsonrpclib.smithy4sinterop.CirceJsonCodec

object BSPCodecsTest extends FunSuite {
  test("BuildTargetTestInput") {
    val input = BuildTargetTestInput(
      data = TestParams.testParamsScalaTestParams(
        targets = Nil
      )
    )

    roundtripTest(
      input,
      json"""{
        "dataKind": "scala-test",
        "targets": []
        }""",
    )
  }
  test("WorkspaceBuildTargetsResult") {
    val targetId = BuildTargetIdentifier(URI("proj://hello"))

    val input = WorkspaceBuildTargetsResult(
      List(
        BuildTarget.buildTargetScalaBuildTarget(
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
            URI(Paths.get("/foo/bar").toUri().toString())
          ),
          data = Some(
            ScalaBuildTarget(
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

    val expected =
      json"""{
        "targets": [
          {
            "dataKind": "scala",
            "tags": ["library"],
            "data": {
              "jars": [],
              "scalaBinaryVersion": "3.7",
              "scalaVersion": "3.7.0-RC1",
              "platform": 1,
              "scalaOrganization": "org.scala-lang"
            },
            "languageIds": ["scala"],
            "id": { "uri": "proj://hello" },
            "baseDirectory": "file:///foo/bar",
            "dependencies": [],
            "displayName": "jk-hello",
            "capabilities": {
              "canCompile": true,
              "canTest": true,
              "canRun": true,
              "canDebug": true
            }
          }
        ]
      }"""

    roundtripTest(input, expected)
  }

  // compilation test
  @nowarn("msg=unused")
  def sanityCheck(t: BuildTarget.BuildTargetScalaBuildTarget): Unit = {
    val bt: JvmBuildTarget = t.data.get.jvmBuildTarget.get
    val jvm: Option[String] = bt.javaVersion
  }

  private def codecFor[A: Schema]: Codec[A] = CirceJsonCodec.fromSchema[A](
    using Schema[A].transformTransitivelyK(BSPCodecs.bspTransformations)
  )

  private def roundtripTest[A: Schema](
    a: A,
    encoded: Json,
  )(
    using SourceLocation
  ): Expectations = {
    val c = codecFor[A]

    val encoded2 = c.apply(a)

    val result = encoded2

    val decoded = c.decodeJson(encoded2)

    assert.same(result, encoded) &&
    assert.same(Right(a), decoded)
  }

}
