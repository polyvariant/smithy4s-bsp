$version: "2"

namespace sample

use traits#data
use traits#dataKind

@data
document RunParamsData

structure RunParams {
    data: RunParamsData
}

@data
document DebugSessionParamsData

structure DebugSessionParams {
    data: DebugSessionParamsData
}

@dataKind(
    kind: "scala-main-class"
    extends: [DebugSessionParamsData, RunParamsData]
)
structure ScalaMainClass {}
