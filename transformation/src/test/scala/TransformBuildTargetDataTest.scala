import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.diff.ModelDiff
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.transform.ModelTransformer
import weaver.*
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.loader.ModelAssembler

object TransformBuildTargetDataTest extends FunSuite {
  test("Sample transformation of data") {
    val result = new TransformBuildTargetData().transform(
      TransformContext
        .builder()
        .model(loadModel(os.sub / "smithy" / "sampleDataTraits" / "input.smithy"))
        .build()
    )

    val expected = loadModel(
      os.sub / "smithy" / "sampleDataTraits" / "expected.smithy"
    )

    val diff =
      ModelDiff
        .builder()
        .oldModel(expected)
        .newModel(result)
        .classLoader(this.getClass().getClassLoader())
        .compare()
        .getDiffEvents()
        .asScala
        .toList

    assert(diff.isEmpty, diff.map(_.toString()).mkString("\n"))
  }

  private def loadModel(resources: os.SubPath*): Model = {
    val assembler = Model
      .assembler(this.getClass().getClassLoader())
      .discoverModels(this.getClass().getClassLoader())
      .putProperty(ModelAssembler.DISABLE_JAR_CACHE, true)

    resources.foreach { res =>
      assembler.addImport(this.getClass().getResource(res.toString()))
    }

    ModelTransformer
      .create
      .filterShapes(
        assembler
          .assemble()
          .unwrap(),
        s =>
          s.getId().getNamespace().startsWith("traits") ||
            !s.getId().getNamespace().startsWith("bsp"),
      )

  }

}
