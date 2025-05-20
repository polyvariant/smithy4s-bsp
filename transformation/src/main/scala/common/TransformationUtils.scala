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

package common

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.Trait
import java.util.function.BiFunction
import scala.collection.JavaConverters.*

object TransformationUtils {

  implicit class ModelOps(private val model: Model) extends AnyVal {

    def mapSomeShapes(f: PartialFunction[Shape, Shape]): Model = ModelTransformer
      .create()
      .mapShapes(model, pfOrIdentity(f).apply(_))

    def mapSomeTraits(funs: PartialFunction[(Shape, Trait), Trait]*): Model = ModelTransformer
      .create()
      .mapTraits(
        model,
        funs
          .map(pf =>
            ((s, trt) => pf.lift((s, trt)).getOrElse(trt)): BiFunction[Shape, Trait, Trait]
          )
          .asJava,
      )

  }

  def pfOrIdentity[A](f: PartialFunction[A, A]): A => A = a => f.lift(a).getOrElse(a)
}
