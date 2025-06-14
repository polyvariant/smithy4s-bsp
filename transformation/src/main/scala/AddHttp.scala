import software.amazon.smithy.build.ProjectionTransformer
import software.amazon.smithy.build.TransformContext
import software.amazon.smithy.model.Model
import common.TransformationUtils.ModelOps
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ServiceShape
import scala.collection.JavaConverters.*
import software.amazon.smithy.model.traits.HttpTrait
import jsonrpclib.JsonNotificationTrait
import software.amazon.smithy.model.pattern.UriPattern
import alloy.SimpleRestJsonTrait
import jsonrpclib.JsonRequestTrait

class AddHttp extends ProjectionTransformer {
  def getName(): String = "add-http"

  def transform(context: TransformContext): Model = {

    val opsUsedInBuildServer =
      context
        .getModel()
        .expectShape(ShapeId.from("bsp#BuildServer"), classOf[ServiceShape])
        .getAllOperations()
        .asScala
        .toSet
    context.getModel().mapSomeShapes {
      case s if s.getId() == ShapeId.from("bsp#BuildServer") =>
        s.asServiceShape().get().toBuilder().addTrait(new SimpleRestJsonTrait()).build()
      case s if opsUsedInBuildServer.contains(s.getId()) =>
        s.asOperationShape()
          .get()
          .toBuilder()
          .addTrait(
            HttpTrait
              .builder()
              .method("POST")
              .uri(
                UriPattern.parse {
                  s.getTrait(classOf[JsonNotificationTrait])
                    .map[String]("/" + _.getValue())
                    .or(() =>
                      s.getTrait(classOf[JsonRequestTrait])
                        .map[String]("/" + _.getValue())
                    )
                    .orElseThrow(() =>
                      new Exception(
                        s"Operation ${s.getId()} does not have a JsonNotificationTrait or JsonRequestTrait. It has: ${s.getAllTraits().asScala.toMap}"
                      )
                    )
                }
              )
              .build()
          )
          .build()
    }
  }

}
