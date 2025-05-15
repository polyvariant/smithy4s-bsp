$version: "2"

namespace sample

use traits#data
use traits#dataKind
use traits#jsonRequest

@data
document MealData

@dataKind(
    kind: "steak"
    extends: [MealData]
)
structure Steak {
}

structure Meal {
    @required
    name: String

    @required
    data: MealData
}

@jsonRequest("meal/make")
operation MakeMeal {
    input: Meal
}
