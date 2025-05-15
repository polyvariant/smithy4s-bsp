$version: "2.0"

namespace sample

use alloy#discriminated
use smithy4s.meta#adt
use smithy4sbsp.meta#dataDefault

@mixin
structure DebugSessionParamsCommon {}

structure DebugSessionParamsOther with [DebugSessionParamsCommon] {
    data: Document
}

structure DebugSessionParamsScalaMainClass with [DebugSessionParamsCommon] {
    @required
    data: ScalaMainClass
}

@mixin
structure RunParamsCommon {}

structure RunParamsOther with [RunParamsCommon] {
    data: Document
}

structure RunParamsScalaMainClass with [RunParamsCommon] {
    @required
    data: ScalaMainClass
}

structure ScalaMainClass {}

@discriminated("dataKind")
@adt
union DebugSessionParams {
    @jsonName("scala-main-class")
    scala_main_class: DebugSessionParamsScalaMainClass

    @jsonName("other")
    @dataDefault
    other: DebugSessionParamsOther
}

@discriminated("dataKind")
@adt
union RunParams {
    @jsonName("scala-main-class")
    scala_main_class: RunParamsScalaMainClass

    @jsonName("other")
    @dataDefault
    other: RunParamsOther
}
