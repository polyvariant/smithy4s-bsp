$version: "2"

metadata validators = [
    {
        name: "EmitEachSelector"
        configuration: { selector: "service[trait|traits#jsonRPC]:not([trait|smithy4s.meta#packedInputs])" }
        message: "BSP services should have packedInputs applied"
        severity: "DANGER"
        id: "BspRequirePackedInputs"
    }
]

namespace smithy4sbsp.meta

use bsp#BuildClient
use bsp#BuildServer
use bsp.cancel#CancelExtension
use bsp.cargo#CargoBuildServer
use bsp.cpp#CppBuildServer
use bsp.java#JavaBuildServer
use bsp.jvm#JvmBuildServer
use bsp.python#PythonBuildServer
use bsp.rust#RustBuildServer
use bsp.scala#ScalaBuildServer
use smithy4s.meta#packedInputs

/// Signifies that the given member should be flattened into the parent.
/// Sort of like @httpPayload, but for jsonRPC.
@trait(selector: "structure > member", structurallyExclusive: "member")
structure rpcPayload {}

apply BuildServer @packedInputs

apply ScalaBuildServer @packedInputs

apply RustBuildServer @packedInputs

apply CppBuildServer @packedInputs

apply JvmBuildServer @packedInputs

apply BuildClient @packedInputs

apply CancelExtension @packedInputs

apply CargoBuildServer @packedInputs

apply JavaBuildServer @packedInputs

apply PythonBuildServer @packedInputs
