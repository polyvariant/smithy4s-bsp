import software.amazon.smithy.build.ProjectionTransformer

import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer
import scala.collection.JavaConverters._
import java.util.stream.Collectors
import software.amazon.smithy.model.shapes.ShapeId
import bsp.traits.DataKindTrait
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TraitService
import java.util.ServiceLoader
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NodeVisitor
import software.amazon.smithy.model.node.ToNode

class TransformBuildTargetData extends ProjectionTransformer {
  def getName(): String = "transform-build-target-data"

  // find all shapes with the trait DataKind
  // create a union of them all
  // replace the bsp.BuildTargetData document with that union
  def transform(context: TransformContext): Model = {
    val m = context.getModel()

    ServiceLoader.load(classOf[TraitService]).asScala.foreach(println)

    val mapping = m
      .getShapesWithTrait(DataKindTrait.ID)
      .asScala
      .flatMap { dataKindImpl =>
        // a bit of a hacky way to do: dataKindImpl.expectTrait(classOf[DataKindTrait])
        val b = Shape.shapeToBuilder(dataKindImpl): AbstractShapeBuilder[_, _]

        b.addTrait(
          new DataKindTrait.Provider().createTrait(
            DataKindTrait.ID,
            dataKindImpl.getAllTraits().asScala(DataKindTrait.ID).toNode(),
          )
        )

        val updatedKind = b.build()
        updatedKind
          .expectTrait(classOf[DataKindTrait])
          .getPolymorphicData()
          .asScala
          .toList
          .map(_ -> updatedKind)
      }
      .groupBy(_._1)
      .map { case (k, vs) => (k, vs.map(_._2)) }

    mapping.foreach { case (k, _) =>
      assert(
        m.expectShape(k).isDocumentShape(),
        s"Expected $k to be a document shape, but it's a ${m.expectShape(k)} instead",
      )
    }

    m.toBuilder()
      .addShapes(
        mapping
          .map { case (k, vs) =>
            val u = UnionShape
              .builder()
              .id(k)

            vs.foreach { v =>
              u.addMember(
                v.expectTrait(classOf[DataKindTrait]).getKind().replace("-", "_"),
                v.getId(),
              )
            }
            u.build()
          }
          .toList
          .asJava
      )
      .build()
  }

}
