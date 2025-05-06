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

import alloy.DiscriminatedUnionTrait
import bsp.traits.DataKindTrait
import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.neighbor.NeighborProvider
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.SmithyIdlModelSerializer
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.DynamicTrait
import software.amazon.smithy.model.traits.JsonNameTrait
import software.amazon.smithy.model.traits.MixinTrait
import software.amazon.smithy.model.traits.RequiredTrait

import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import scala.collection.JavaConverters.*

class TransformBuildTargetData extends ProjectionTransformer {
  def getName(): String = "transform-build-target-data"

  def transform(context: TransformContext): Model = {
    val m = context.getModel()

    // BuildTargetData -> Set(ScalaBuildTarget, CppBuildTarget, ...)
    val mapping = makeDataKindMapping(m)

    // All references to the shapes in the mapping keys.
    // e.g. BuildTarget$data
    val references =
      mapping
        .keySet
        .flatMap { dataType =>
          NeighborProvider
            .reverse(m)
            .getNeighbors(m.expectShape(dataType))
            .asScala
            // we know for a fact these are members... for now. Usually even named `data`.
            .map(_.getShape().asMemberShape().get())
        }
        .toSet

    val mb = Model.builder().addShapes(m.shapes().collect(Collectors.toList()))

    references.foreach { baseDataMember =>
      transformRef(baseDataMember, mapping(baseDataMember.getTarget()), mb, m)
    }

    val transformed = mb.build()

    dump(transformed)

    transformed
  }

  // Handle an individual reference to a data member.
  // For example,
  // baseDataMember: BuildTarget$data (the member targets the shape BuildTargetData)
  // targetRefs: ScalaBuildTarget, CppBuildTarget.
  private def transformRef(
    baseDataMember: MemberShape,
    targetRefs: Set[Shape],
    mb: Model.Builder,
    m: Model,
  ): Unit = {
    // baseDataMember: e.g. BuildTarget$data.

    // parent: e.g. BuildTarget. It is a struct,
    // it needs to become a union of N shapes.
    // N is equal to the size of mapping(target.getId()).
    val parent = m.expectShape(baseDataMember.getId().withoutMember()).asStructureShape().get()

    val mixinForMembers = makeMixinForMembers(
      originalOwner = parent,
      originalDataMember = baseDataMember,
    )

    mb.addShape(mixinForMembers)

    val unionBuilder = UnionShape
      .builder()
      .id(parent.getId())

    unionBuilder
      .addTrait(
        new DiscriminatedUnionTrait("dataKind")
      )

    targetRefs.foreach { targetRef =>
      makeNewUnionTarget(targetRef, List(mixinForMembers), baseDataMember)
        .foreach(mb.addShape)

      val dataKind = expectDataKind(targetRef).getKind()

      unionBuilder.addMember(
        // escaping, the smithy model doesn't like hyphens in member names
        dataKind.replace("-", "_"),
        // at this point this won't be the same shape as targetRef
        // but the ID is the same.
        targetRef.getId(),
        _.addTrait(
          new JsonNameTrait(dataKind)
        ),
      )
    }

    val u = unionBuilder.build()
    mb.addShape(u)

    updateOperations(u, mb, m)
  }

  // Creates a new structure shape that will become a target of the new union. For example, ScalaBuildTarget.
  // It will have all the BuildTarget fields (due to the mixin), and a `data: ScalaBuildTargetData`` field.
  // returns the shapes that were created, in no particular order.
  private def makeNewUnionTarget(
    targetRef: Shape,
    mixins: List[Shape],
    baseDataMember: MemberShape,
  ): List[Shape] = {
    val newDataStruct = makeDataStruct(targetRef)

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
      .mixins(mixins.asJava)

    newStructBuilder.addMember(
      baseDataMember.getMemberName(),
      newDataStruct.getId(),
      _.addTraits(baseDataMember.getIntroducedTraits().values()),
    )

    val newStruct = newStructBuilder.build()

    List(
      newStruct,
      newDataStruct,
    )
  }

  // Copies all the members of targetRef into a new structure shape
  // renamed with a Data suffix to avoid conflicting with the enclosing struct.
  // Used to copy `ScalaBuildTarget` into `ScalaBuildTargetData`.
  private def makeDataStruct(targetRef: Shape): StructureShape = {
    val builder = StructureShape
      .builder()
      .id(
        ShapeId
          .fromParts(targetRef.getId().getNamespace(), targetRef.getId().getName() + "Data")
      )

    targetRef
      .members()
      .asScala
      .foreach { m =>
        builder.addMember(
          m.getMemberName(),
          m.getTarget(),
          _.addTraits(m.getIntroducedTraits().values()),
        )
      }

    builder.build()
  }

  // Creates a mapping of each shape marked with DataKind, to shapes that extend that kind.
  private def makeDataKindMapping(m: Model): Map[ShapeId, Set[Shape]] = {
    val mapping = m
      .getShapesWithTrait(DataKindTrait.ID)
      .asScala
      .flatMap { dataKindImpl =>
        expectDataKind(dataKindImpl)
          .getPolymorphicData()
          .asScala
          .toList
          .map(_ -> dataKindImpl)
      }
      .groupBy(_._1)
      .map { case (k, vs) => (k, vs.map(_._2).toSet) }

    mapping.foreach { case (k, _) =>
      assert(
        m.expectShape(k).isDocumentShape(),
        s"Expected $k to be a document shape, but it's a ${m.expectShape(k)} instead",
      )
    }
    mapping
  }

  // Creates a mixin with all the fields of the original owner, except for the data member.
  private def makeMixinForMembers(originalOwner: Shape, originalDataMember: Shape)
    : StructureShape = {
    val builder = StructureShape
      .builder()
      .id(originalOwner.getId() + "Common")
      .addTrait(MixinTrait.builder.build())

    def addNonDataMembers(toStruct: StructureShape.Builder): Unit = originalOwner
      .members()
      .asScala
      .filterNot(_.getId() == originalDataMember.getId())
      .foreach { member =>
        toStruct.addMember(
          member.getMemberName(),
          member.getTarget(),
          _.traits(member.getIntroducedTraits().values()),
        )
      }

    addNonDataMembers(builder)

    builder.build()
  }

  // If the union is now attempting to become an operation input/output, we need to wrap it in a payload wrapper struct.
  private def updateOperations(u: UnionShape, mb: Model.Builder, sourceModel: Model): Unit = {
    val original = sourceModel.expectShape(u.getId())

    val opReferences = NeighborProvider
      .reverse(sourceModel)
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

  // wraps the given operation's input/output in a single-field structure shape with @rpcPayload.
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

  private def expectDataKind(s: Shape): DataKindTrait = {
    val trt = Option(
      s.getAllTraits().get(DataKindTrait.ID)
    ).getOrElse(sys.error(s"Expected $s to have a DataKind trait, but it doesn't"))

    new DataKindTrait.Provider().createTrait(
      trt.toShapeId(),
      trt.toNode(),
    )
  }

  // for debugging modified smithy
  private def dump(m: Model): Unit = {
    val debug = false
    if (debug) {
      Files.createDirectories(Paths.get("smithyoutput"))
      SmithyIdlModelSerializer
        .builder()
        .basePath(Paths.get("smithyoutput"))
        .build()
        .serialize(m)
        .asScala
        .foreach { case (k, v) => Files.write(k, v.getBytes()) }
    }
  }

}
