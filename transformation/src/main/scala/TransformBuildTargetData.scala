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
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.shapes.AbstractShapeBuilder
import smithy4s.meta.AdtTrait
import software.amazon.smithy.model.traits.InputTrait
import software.amazon.smithy.model.loader.Prelude
import jsonrpclib.JsonRpcPayloadTrait

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

    val mb = m.toBuilder()
    // removing the @data shapes
    mapping.keySet.foreach(mb.removeShape)

    references.foreach { baseDataMember =>
      transformRef(baseDataMember, mapping(baseDataMember.getTarget()), mb, m)
    }

    val transformed = mb.build()

    dump(transformed)
    // todo: test for @data without any kinds
    // todo: what to do when data is optional and missing?

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

    // need to remove this shape so that its leftover members also disappear
    mb.removeShape(parent.getId())

    val unionBuilder = UnionShape
      .builder()
      .id(parent.getId())

    unionBuilder
      .addTrait(
        new DiscriminatedUnionTrait("dataKind")
      )
      .addTrait(
        new AdtTrait()
      )

    targetRefs.toList.sorted.foreach { targetRef =>
      mb.removeShape(targetRef.getId())
      val (newDataStruct, newTarget) = makeNewUnionTarget(
        parent.getId(),
        targetRef,
        List(mixinForMembers),
        baseDataMember,
      )

      List(newDataStruct, newTarget)
        .foreach(mb.addShape)

      val dataKind = expectDataKind(targetRef).getKind()

      unionBuilder.addMember(
        // escaping, the smithy model doesn't like hyphens in member names
        dataKind.replace("-", "_"),
        // at this point this won't be the same shape as targetRef
        // but the ID is the same.
        newTarget.getId(),
        _.addTrait(
          new JsonNameTrait(dataKind)
        ),
      )
    }

    if (!baseDataMember.isRequired()) {
      val otherTarget = makeOtherUnionTarget(parent.getId(), List(mixinForMembers))

      mb.addShape(otherTarget)

      unionBuilder.addMember(
        "other",
        otherTarget.getId(),
        _.addTrait(new JsonNameTrait("other"))
          .addTrait(
            new DynamicTrait(ShapeId.from("smithy4sbsp.meta#dataDefault"), Node.objectNode())
          ),
      )
    }

    val u = unionBuilder.build()
    mb.addShape(u)

    updateOperations(u, mb, m)
  }

  // Creates a new structure shape that will become a target of the new union. For example, ScalaBuildTarget.
  // It will have all the BuildTarget fields (due to the mixin), and a `data: ScalaBuildTargetData` field.
  // returns the union target's `data` field target, and the union target itself.
  private def makeNewUnionTarget(
    unionName: ShapeId,
    targetRef: Shape,
    mixins: List[Shape],
    baseDataMember: MemberShape,
  ): (Shape, Shape) = {
    val newDataStruct = makeDataStruct(targetRef)

    val newStructBuilder = StructureShape
      .builder()
      .id {
        val base = targetRef.getId()
        val newName = base.getName()
        ShapeId.fromParts(base.getNamespace(), unionName.getName + newName)
      }

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
      _.addTraits(baseDataMember.getIntroducedTraits().values())
        // We add the trait no matter if it was there. If it wasn't there, there's gonna be a separate union case that handles that
        .addTrait(new RequiredTrait()),
    )

    val newStruct = newStructBuilder.build()

    (
      newDataStruct,
      newStruct,
    )
  }

  private def makeOtherUnionTarget(
    unionName: ShapeId,
    mixins: List[Shape],
  ): Shape = {
    // like makeNewUnionTarget, but the new member only has an optional `data: Document` field.
    // This is supposed to help if the dataKind is missing (after a runtime transformation).

    val newStructBuilder = StructureShape
      .builder()
      .id {
        val base = unionName
        val newName = base.getName() + "Other"
        ShapeId.fromParts(base.getNamespace(), newName)
      }
      .addMember(
        "data",
        ShapeId.fromParts(Prelude.NAMESPACE, "Document"),
      )

    newStructBuilder
      .mixins(mixins.asJava)
      .build()
  }

  // Removes the dataKind trait from the data struct.
  private def makeDataStruct(targetRef: Shape): Shape = {
    val b = Shape.shapeToBuilder(targetRef): AbstractShapeBuilder[_, _]
    b.removeTrait(DataKindTrait.ID).build()
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
    .id(ShapeId.fromParts(op.getId().getNamespace(), op.getId().getName() + suffix))
    .tap(
      _.addMember(
        "data",
        wraps,
        _.addTrait(new RequiredTrait())
          .addTrait(JsonRpcPayloadTrait.builder().build()),
      )
    )
    .tap(_.addTrait(new InputTrait()))
    .build()

  private def optionalToOption[T](o: java.util.Optional[T]): Option[T] =
    if (o.isPresent())
      Some(o.get())
    else
      None

  private def expectDataKind(s: Shape): DataKindTrait = s.expectTrait(classOf[DataKindTrait])

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

  // scala.util.chaining for 2.12
  final implicit class ChainingOps[A](private val a: A) {

    def tap[B](f: A => B): A = {
      f(a)
      a
    }

  }

}
