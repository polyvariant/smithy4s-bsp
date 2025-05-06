$version: "2.0"

namespace sample

use alloy#discriminated
use smithy4s.meta#adt

@mixin
structure DebugSessionParamsCommon {}

structure DebugSessionParamsScalaMainClass with [DebugSessionParamsCommon] {
    data: ScalaMainClass
}

@mixin
structure RunParamsCommon {}

structure RunParamsScalaMainClass with [RunParamsCommon] {
    data: ScalaMainClass
}

structure ScalaMainClass {}

@adt
@discriminated("dataKind")
union DebugSessionParams {
    @jsonName("scala-main-class")
    scala_main_class: DebugSessionParamsScalaMainClass
}

@adt
@discriminated("dataKind")
union RunParams {
    @jsonName("scala-main-class")
    scala_main_class: RunParamsScalaMainClass
}
