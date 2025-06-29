$version: "2.0"

namespace sample

use alloy#discriminated
use jsonrpclib#jsonRpcPayload
use smithy4s.meta#adt
use traits#jsonRequest

@mixin
structure MealCommon {
    @required
    name: String
}

structure MealSteak with [MealCommon] {
    @required
    data: Steak
}

structure Steak {}

@adt
@discriminated("dataKind")
union Meal {
    @jsonName("steak")
    steak: MealSteak
}

@jsonRequest("meal/make")
operation MakeMeal {
    input := {
        @required
        @jsonRpcPayload
        data: Meal
    }
}
