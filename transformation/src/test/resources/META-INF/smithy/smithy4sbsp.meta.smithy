$version: "2"


namespace smithy4sbsp.meta

// todo: share this with codegen

/// Signifies that the given member should be flattened into the parent.
/// Sort of like @httpPayload, but for jsonRPC.
@trait(selector: "structure > member", structurallyExclusive: "member")
structure rpcPayload {}
