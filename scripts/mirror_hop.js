// configure the first choice
const base = [
    [[-3, 63, 8], [-1, 63, 10]],
    [[2, 63, 8], [4, 63, 10]]
]

// configure the vector to generate new elements
const vec = [0, 0, 5]

// the amount of choices
const amount = 16;

function offset(tuple, times) {
    return [
        tuple[0] + vec[0] * times,
        tuple[1] + vec[1] * times,
        tuple[2] + vec[2] * times
    ]
}

const array = []

for (let i = 0; i < amount; i++) {
    array.push([
        [offset(base[0][0], i), offset(base[0][1], i)],
        [offset(base[1][0], i), offset(base[1][1], i)],
    ])
}

console.log(JSON.stringify(array))
