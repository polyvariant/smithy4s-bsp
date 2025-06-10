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

import cats.syntax.all.*
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
import io.circe.literal.*
import smithy4s.schema.Schema
import weaver.*

import java.nio.file.Paths
import scala.annotation.nowarn
import bsp.CodeDescription
import bsp.DiagnosticTag
import bsp.DiagnosticRelatedInformation
import bsp.Location
import bsp.DiagnosticCode
import bsp.scala_.ScalaDiagnostic
import bsp.scala_.ScalaAction
import bsp.scala_.ScalaWorkspaceEdit
import bsp.scala_.ScalaTextEdit
import bsp.Diagnostic
import bsp.scala_.ScalaTestParams
import smithy4s.Document
import smithy4s.Document.DNull
import smithy4s.Document.DString
import smithy4s.Document.DBoolean
import smithy4s.Document.DNumber
import smithy4s.Document.DArray
import smithy4s.Document.DObject

object BSPCodecsTest extends FunSuite {
  test("BuildTargetTestInput") {
    val input = BuildTargetTestInput(
      data = TestParams.testParamsScalaTestParams(
        targets = Nil,
        data = ScalaTestParams(),
      )
    )

    roundtripTest(
      input,
      json"""{
        "targets": [],
        "dataKind": "scala-test",
        "data": {}
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
          data = ScalaBuildTarget(
            scalaOrganization = "org.scala-lang",
            scalaVersion = "3.7.0-RC1",
            scalaBinaryVersion = "3.7",
            platform = ScalaPlatform.JVM,
            jars = Nil,
            jvmBuildTarget = None,
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

  test("Diagnostics") {
    val input = bsp.Diagnostics(
      List(
        bsp
          .Diagnostic
          .diagnosticScalaDiagnostic(
            range = bsp.Range(
              start = bsp.Position(bsp.Integer(0), bsp.Integer(0)),
              end = bsp.Position(bsp.Integer(1), bsp.Integer(1)),
            ),
            message = "division by zero",
            severity = Some(bsp.DiagnosticSeverity.ERROR),
            code = Some(DiagnosticCode.string("code")),
            codeDescription = Some(CodeDescription(URI("proj://hello"))),
            source = Some("src"),
            tags = Some(
              List(
                DiagnosticTag.DEPRECATED
              )
            ),
            relatedInformation = Some(
              List(
                DiagnosticRelatedInformation(
                  location = Location(
                    uri = URI("proj://hello"),
                    range = bsp.Range(
                      start = bsp.Position(bsp.Integer(0), bsp.Integer(0)),
                      end = bsp.Position(bsp.Integer(1), bsp.Integer(1)),
                    ),
                  ),
                  message = "look here",
                )
              )
            ),
            data = ScalaDiagnostic(
              actions = Some(
                List(
                  ScalaAction(
                    title = "fix",
                    description = Some("fix it"),
                    edit = Some(
                      ScalaWorkspaceEdit(
                        changes = List(
                          ScalaTextEdit(
                            range = bsp.Range(
                              start = bsp.Position(bsp.Integer(0), bsp.Integer(0)),
                              end = bsp.Position(bsp.Integer(1), bsp.Integer(1)),
                            ),
                            newText = "new text",
                          )
                        )
                      )
                    ),
                  )
                )
              )
            ),
          )
      )
    )

    roundtripTest(
      input,
      json"""[
          {
            "source": "src",
            "dataKind": "scala",
            "tags": [ 2 ],
            "data": {
              "actions": [
                {
                  "title": "fix",
                  "description": "fix it",
                  "edit": {
                    "changes": [
                      {
                        "range": {
                          "start": { "line": 0, "character": 0 },
                          "end": { "line": 1, "character": 1 }
                        },
                        "newText": "new text"
                      }
                    ]
                  }
                }
              ]
            },
            "code": "code",
            "range": {
              "start": { "line": 0, "character": 0 },
              "end": { "line": 1, "character": 1 }
            },
            "message": "division by zero",
            "severity": 1,
            "codeDescription": { "href": "proj://hello" },
            "relatedInformation": [
              {
                "location": {
                  "uri": "proj://hello",
                  "range": {
                    "start": { "line": 0, "character": 0 },
                    "end": { "line": 1, "character": 1 }
                  }
                },
                "message": "look here"
              }
            ]
          }
        ]""",
    )
  }

  test("Diagnostic (real sample from bloop)") {
    val encoded =
      json"""{
        "textDocument": {
          "uri": "file:///Users/kubukoz/projects/smithy-playground/modules/ast/src/main/scala/playground/smithyql/AST.scala"
        },
        "buildTarget": {
          "uri": "file:/Users/kubukoz/projects/smithy-playground/modules/ast/?id=ast"
        },
        "diagnostics": [
          {
            "source": "bloop",
            "range": {
              "start": { "line": 183, "character": 24 },
              "end": { "line": 183, "character": 24 }
            },
            "severity": 2,
            "code": "198",
            "message": "unused implicit parameter",
            "data": { "actions": [] }
          }
        ],
        "reset": false
      }"""

    val input = bsp.PublishDiagnosticsParams(
      textDocument = bsp.TextDocumentIdentifier(
        uri = URI(
          "file:///Users/kubukoz/projects/smithy-playground/modules/ast/src/main/scala/playground/smithyql/AST.scala"
        )
      ),
      buildTarget = bsp.BuildTargetIdentifier(
        uri = URI("file:/Users/kubukoz/projects/smithy-playground/modules/ast/?id=ast")
      ),
      diagnostics = List(
        Diagnostic.diagnosticOther(
          range = bsp.Range(
            start = bsp.Position(bsp.Integer(183), bsp.Integer(24)),
            end = bsp.Position(bsp.Integer(183), bsp.Integer(24)),
          ),
          severity = Some(bsp.DiagnosticSeverity.WARNING),
          code = Some(DiagnosticCode.string("198")),
          source = Some("bloop"),
          message = "unused implicit parameter",
          data =
            Document
              .obj(
                "actions" -> Document.array()
              )
              .some,
        )
      ),
      reset = false,
    )

    roundtripTest(
      input,
      encoded,
    )
  }

  // compilation test
  @nowarn("msg=unused")
  def sanityCheck(t: BuildTarget.BuildTargetScalaBuildTarget): Unit = {
    val bt: JvmBuildTarget = t.data.jvmBuildTarget.get
    val jvm: Option[String] = bt.javaVersion
  }

  private def codecFor[A: Schema]: (Document.Encoder[A], Document.Decoder[A]) = {
    val schema = BSPCodecs.bspTransformations(Schema[A])
    println(schema)
    (Document.Encoder.fromSchema(schema), Document.Decoder.fromSchema(schema))
  }

  private def roundtripTest[A: Schema](
    a: A,
    encoded: Json,
  )(
    using SourceLocation
  ): Expectations = {
    val (encoder, decoder) = codecFor[A]

    val result = encoder.encode(a)

    val resultAsJson = documentToJson(result)

    val decoded = decoder.decode(result)

    expect.same(resultAsJson, encoded) &&
    expect.same(Right(a), decoded)
  }

  private val documentToJson: Document => Json = {
    case DNull            => Json.Null
    case DString(value)   => Json.fromString(value)
    case DBoolean(value)  => Json.fromBoolean(value)
    case DNumber(value)   => Json.fromBigDecimal(value)
    case DArray(values)   => Json.fromValues(values.map(documentToJson))
    case DObject(entries) => Json.fromFields(entries.view.mapValues(documentToJson))
  }

}
