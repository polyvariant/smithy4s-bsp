$version: "2"

namespace smithy4sbsp.meta

use bsp#BuildClient
use bsp#BuildServer
use bsp.cpp#CppBuildServer
use bsp.jvm#JvmBuildServer
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
