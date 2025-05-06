$version: "2.0"

namespace sample

use alloy#discriminated
use smithy4s.meta#adt

structure FullDinner {
    ramen: Ramen
    steak: Steak
}

@mixin
structure MealCommon {
    @required
    name: String
}

structure MealFullDinner with [MealCommon] {
    @required
    data: FullDinner
}

structure MealRamen with [MealCommon] {
    @required
    data: Ramen
}

structure MealSteak with [MealCommon] {
    @required
    data: Steak
}

structure Ramen {
    @required
    pork: Boolean

    @required
    chicken: Boolean
}

structure Steak {
    @required
    cookLevel: String
}

@adt
@discriminated("dataKind")
union Meal {
    @jsonName("full-dinner")
    full_dinner: MealFullDinner

    @jsonName("ramen")
    ramen: MealRamen

    @jsonName("steak")
    steak: MealSteak
}
