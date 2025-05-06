import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.traits.UniqueItemsTrait
import software.amazon.smithy.model.transform.ModelTransformer

class SetShapes extends ProjectionTransformer {
  def getName(): String = "set-shapes"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapShapes(
      context.getModel(),
      s =>
        s match {
          case s if s.hasTrait(bsp.traits.SetTrait.ID) =>
            val builder = s.asListShape().get().toBuilder()
            builder.removeTrait(bsp.traits.SetTrait.ID)
            builder.addTrait(new UniqueItemsTrait())
            builder.build()
          case s => s
        },
    )

}
