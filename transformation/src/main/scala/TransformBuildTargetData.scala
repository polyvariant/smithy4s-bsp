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
import software.amazon.smithy.model.neighbor.NeighborProvider
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import java.nio.file.Paths
import java.nio.file.Files
import software.amazon.smithy.model.traits.DynamicTrait
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.RequiredTrait
import alloy.DiscriminatedUnionTrait
import software.amazon.smithy.model.traits.JsonNameTrait
import software.amazon.smithy.model.traits.MixinTrait

class TransformBuildTargetData extends ProjectionTransformer {
  def getName(): String = "transform-build-target-data"

  def transform(context: TransformContext): Model = {
    val m = context.getModel()

    ServiceLoader.load(classOf[TraitService]).asScala.foreach(println)

    // BuildTargetData -> Set(ScalaBuildTarget, CppBuildTarget, ...)
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

    val mb = Model.builder().addShapes(m.shapes().toList())

    // BuildTarget$data -> BuildTargetData
    val references =
      mapping.flatMap { case (dataType, _) =>
        val np = NeighborProvider
          .reverse(m)

        np
          .getNeighbors(m.expectShape(dataType))
          .asScala
          .map(_.getShape().asMemberShape().get())
      }.toSet

    val newShapes = references.map { baseDataMember =>
      // baseDataMember: e.g. BuildTarget$data.

      // parent: e.g. BuildTarget. It is a struct,
      // it needs to become a union of N shapes.
      // N is equal to the size of mapping(baseDataType).
      val parent = m.expectShape(baseDataMember.getId().withoutMember()).asStructureShape().get()

      // target: e.g. BuildTargetData. It's a document.
      // this shape effectively disappears from the model.
      val target = m.expectShape(baseDataMember.getTarget()).asDocumentShape().get()

      val targetRefs = mapping(target.getId())

      val mixinForMembersBuilder = StructureShape
        .builder()
        .id(parent.getId() + "Common")
        .addTrait(MixinTrait.builder.build())

      def addNonDataMembers(toStruct: StructureShape.Builder): Unit = parent
        .members()
        .asScala
        .filterNot(_.getId() == baseDataMember.getId())
        .foreach { member =>
          toStruct.addMember(
            member.getMemberName(),
            member.getTarget(),
            _.traits(member.getIntroducedTraits().values()),
          )
        }

      addNonDataMembers(mixinForMembersBuilder)

      val mixinForMembers = mixinForMembersBuilder.build()

      mb.addShape(mixinForMembers)

      val unionTargets = targetRefs.map { targetRef =>
        val newStructBuilder = StructureShape
          .builder()
          .id(targetRef.getId())

        newStructBuilder
          .traits(
            targetRef
              .getIntroducedTraits()
              .asScala
              .values
              .filterNot(_.toShapeId() == DataKindTrait.ID)
              .toList
              .asJava
          )
          // the mixin adds all the common fields, so we don't need to do it ourselves
          .mixins(List(mixinForMembers).asJava)

        val newDataStructBuilder = StructureShape
          .builder()
          .id(
            ShapeId
              .fromParts(targetRef.getId().getNamespace(), targetRef.getId().getName() + "Data")
          )

        newDataStructBuilder.members(
          targetRef
            .members()
            .asScala
            .map { m =>
              m.toBuilder.id(newDataStructBuilder.getId().withMember(m.getMemberName())).build()
            }
            .toList
            .asJava
        )

        val newDataStruct = newDataStructBuilder.build()
        mb.addShape(newDataStruct)

        newStructBuilder.addMember(
          baseDataMember.getMemberName(),
          newDataStruct.getId(),
          _.addTraits(baseDataMember.getIntroducedTraits().values()),
        )

        val newStruct = newStructBuilder.build()

        mb.removeShape(targetRef.getId())
        mb.addShape(newStruct)

        targetRef -> newStruct
      }

      val unionBuilder = UnionShape.builder().id(parent.getId())

      unionBuilder.addTrait(
        new DiscriminatedUnionTrait("dataKind")
      )

      unionTargets.foreach { case (original, targetRef) =>
        val dataKind = original.expectTrait(classOf[DataKindTrait]).getKind()
        unionBuilder.addMember(
          dataKind.replace("-", "_"),
          targetRef.getId(),
          _.addTrait(
            new JsonNameTrait(dataKind)
          ),
        )
      }
      unionBuilder.build()
    }

    newShapes.foreach { u =>
      val original = m.expectShape(u.getId())
      mb.removeShape(original.getId())
      mb.addShape(u)

      val opReferences = NeighborProvider
        .reverse(m)
        .getNeighbors(original)
        .asScala
        .map(_.getShape().asOperationShape())
        .flatMap(optionalToOption(_))

      opReferences.foreach { op =>
        if (op.getInputShape() == original.getId()) {
          val wrapper = makeRpcPayloadWrapper(op, u.getId(), "Input")
          mb.addShape(wrapper)
          mb.addShape(op.toBuilder().input(wrapper.getId()).build())
        }

        if (op.getOutputShape() == original.getId()) {
          val wrapper = makeRpcPayloadWrapper(op, u.getId(), "Output")
          mb.addShape(wrapper)
          mb.addShape(op.toBuilder().output(wrapper.getId()).build())
        }
      }
    }

    val transformed = mb.build()

    // for debugging modified smithy
    val debug = false
    if (debug) {
      Files.createDirectories(Paths.get("smithyoutput"))
      SmithyIdlModelSerializer
        .builder()
        .basePath(Paths.get("smithyoutput"))
        .build()
        .serialize(transformed)
        .asScala
        .foreach { case (k, v) => Files.writeString(k, v) }
    }

    transformed

  }

  private def makeRpcPayloadWrapper(op: OperationShape, wraps: ShapeId, suffix: String)
    : StructureShape = StructureShape
    .builder()
    .id(ShapeId.fromParts(op.getId().getName(), op.getId().getName() + suffix))
    .addMember(
      "data",
      wraps,
      _.addTrait(new RequiredTrait())
        .addTrait(
          new DynamicTrait(
            ShapeId.from("smithy4sbsp.meta#rpcPayload"),
            Node.objectNode(),
          )
        ),
    )
    .build()

  private def optionalToOption[T](o: java.util.Optional[T]): Option[T] =
    if (o.isPresent())
      Some(o.get())
    else
      None

}
