$version: "2.0"

namespace sample

use alloy#discriminated

structure FullDinner with [MealCommon] {
    @required
    data: FullDinnerData
}

structure FullDinnerData {
    ramen: RamenData
    steak: SteakData
}

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
    @jsonName("full-dinner")
    full_dinner: FullDinner

    @jsonName("ramen")
    ramen: Ramen

    @jsonName("steak")
    steak: Steak
}
