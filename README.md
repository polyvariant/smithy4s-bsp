# smithy4s-bsp

Smithy definitions for the Build Server Protocol (BSP), enabling type-safe BSP client and server implementations in Scala.

## Overview

This project converts the official [BSP specification](https://build-server-protocol.github.io/) into Smithy format, allowing you to generate type-safe Scala code using [Smithy4s](https://github.com/disneystreaming/smithy4s). The generated code can be used with [jsonrpclib](https://github.com/neandertech/jsonrpclib)'s smithy4s integration to build BSP clients and servers.

## Published Modules

### `codegen`

Contains the Smithy4s-generated BSP data types and protocol definitions.

**sbt:**
```scala
libraryDependencies += "org.polyvariant.smithy4s-bsp" %% "codegen" % "x.y.z"
```

**Mill:**
```scala
ivy"org.polyvariant.smithy4s-bsp::codegen:x.y.z"
```

**scala-cli:**
```scala
//> using dep org.polyvariant.smithy4s-bsp::codegen:x.y.z
```

This artifact includes:
- Complete BSP protocol data types generated from Smithy definitions
- Type-safe representations of all BSP requests, responses, and notifications

### `bsp4s`

The main entrypoint providing codecs and utilities for building BSP clients and servers.

**sbt:**
```scala
libraryDependencies += "org.polyvariant.smithy4s-bsp" %% "bsp4s" % "x.y.z"
```

**Mill:**
```scala
ivy"org.polyvariant.smithy4s-bsp::bsp4s:x.y.z"
```

**scala-cli:**
```scala
//> using dep org.polyvariant.smithy4s-bsp::bsp4s:x.y.z
```

This artifact includes:
- `BSPCodecs` - Helper utilities for creating BSP clients and servers with proper encoding/decoding
- Depends on `codegen`, so you get all the generated types automatically

### `protocol`

Contains custom Smithy trait definitions used in the BSP specification (not typically needed as a direct dependency).

## Usage

To build BSP clients or servers, use the `bsp4s` artifact. You'll also need:

1. [jsonrpclib](https://github.com/neandertech/jsonrpclib) for JSON-RPC communication
2. A transport layer (e.g., fs2-io for stdio communication)

The library provides `BSPCodecs.clientStub` and `BSPCodecs.serverEndpoints` helpers that handle BSP-specific encoding requirements, making it easy to create type-safe BSP clients and servers.

## Examples

See the `example` module for a complete working example of [a BSP server](examples/src/main/scala/sampleServer/SampleServer.scala) and [a BSP client](examples/src/main/scala/sampleServer/SampleClient.scala).

For a real-world usage example, check out [SLS (Simple Language Server)](https://github.com/simple-scala-tooling/sls), a Scala language server that uses this library to communicate with BSP build servers.

## BSP Version

This project tracks the official BSP specification. The Smithy definitions are kept up-to-date with the latest BSP protocol version.

## Dependencies

This project relies on:
- [jsonrpclib](https://github.com/neandertech/jsonrpclib) - JSON-RPC implementation
- [Smithy4s](https://github.com/disneystreaming/smithy4s) - Smithy code generation for Scala
- [alloy-core](https://github.com/disneystreaming/alloy) - Additional Smithy traits

## License

Apache 2.0 - See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! The Smithy definitions are transformed from the official BSP specification.

## Maintainers

- [Jakub Kozłowski](https://github.com/kubukoz)
