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
