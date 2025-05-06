import alloy.OpenEnumTrait
import bsp.traits.EnumKindTrait
import bsp.traits.EnumKindTrait.EnumKind.CLOSED
import bsp.traits.EnumKindTrait.EnumKind.OPEN
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.transform.ModelTransformer

import scala.collection.JavaConverters.*

class OpenEnums extends ProjectionTransformer {
  def getName(): String = "open-enums"

  def transform(context: TransformContext): Model = ModelTransformer
    .create()
    .mapShapes(
      context.getModel(),
      s =>
        s match {
          case s if s.hasTrait(bsp.traits.EnumKindTrait.ID) =>
            val builder = Shape.shapeToBuilder(s): AbstractShapeBuilder[_, _]
            builder.removeTrait(bsp.traits.EnumKindTrait.ID)

            val dynTrait = s
              .getAllTraits()
              .asScala
              .apply(bsp.traits.EnumKindTrait.ID)

            new EnumKindTrait.Provider()
              .createTrait(
                dynTrait.toShapeId(),
                dynTrait.toNode(),
              )
              .getEnumKind() match {
              case OPEN   => builder.addTrait(new OpenEnumTrait())
              case CLOSED => () // do nothing, we remove the trait anyway
            }

            builder.build()

          case s => s
        },
    )

}
