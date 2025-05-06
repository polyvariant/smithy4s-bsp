import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer

class UntaggedUnions extends ProjectionTransformer {
  def getName(): String = "untagged-unions"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapShapes(
      context.getModel(),
      s =>
        s match {
          case s if s.hasTrait(bsp.traits.UntaggedUnionTrait.ID) =>
            val builder = s.asUnionShape().get().toBuilder()
            builder.removeTrait(bsp.traits.UntaggedUnionTrait.ID)
            builder.addTrait(new alloy.UntaggedUnionTrait())
            builder.build()
          case s => s
        },
    )

}
