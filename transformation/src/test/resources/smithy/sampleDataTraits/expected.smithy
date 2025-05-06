$version: "2.0"

namespace sample

use alloy#discriminated

@mixin
structure MealCommon {
    @required
    name: String
}

structure Ramen with [MealCommon] {
    @required
    data: RamenData
}

structure RamenData {
    @required
    pork: Boolean

    @required
    chicken: Boolean
}

structure Steak with [MealCommon] {
    @required
    data: SteakData
}

structure SteakData {
    @required
    cookLevel: String
}

@discriminated("dataKind")
union Meal {
    @jsonName("ra-men")
    ra_men: Ramen

    @jsonName("steak")
    steak: Steak
}
