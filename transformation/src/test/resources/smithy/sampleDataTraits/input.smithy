$version: "2"

namespace sample

use traits#data
use traits#dataKind

@data
document MealData

@dataKind(
    kind: "steak"
    extends: [MealData]
)
structure Steak {
    @required
    cookLevel: String
}

@dataKind(
    kind: "ramen"
    extends: [MealData]
)
structure Ramen {
    @required
    pork: Boolean

    @required
    chicken: Boolean
}

@dataKind(
    kind: "full-dinner"
    extends: [MealData]
)
structure FullDinner {
    ramen: Ramen
    steak: Steak
}

structure Meal {
    @required
    name: String

    @required
    data: MealData
}
