package com.pczumbel.navigator.route

/**
 * Shortest visiting order through a set of points: an open path from a fixed start, optionally
 * returning to a fixed end (for score events that finish where they started).
 *
 * Pure Kotlin with no Android dependencies, so it runs as a plain JVM unit test.
 *
 * Strategy by size:
 *  - n ≤ [EXACT_LIMIT]: Held–Karp dynamic programming — provably optimal, and at 12 points it
 *    is ~600k states, instant on a phone.
 *  - larger: nearest-neighbour seed, then 2-opt and Or-opt local search until no improving move
 *    remains. Not guaranteed optimal, but at orienteering scale (≤ 30 controls) it typically
 *    lands within a few percent, in milliseconds.
 */
object TourSolver {

    const val EXACT_LIMIT = 12

    /**
     * @param dist distance matrix over `n + 2` nodes laid out as: index 0 = start, indices
     *   1..n = the points to visit, index n+1 = end (only read when [returnToEnd] is true).
     * @return the visiting order as indices into the *points* list (0-based, i.e. matrix index − 1).
     */
    fun solve(dist: Array<DoubleArray>, n: Int, returnToEnd: Boolean): List<Int> {
        if (n == 0) return emptyList()
        if (n == 1) return listOf(0)
        val order = if (n <= EXACT_LIMIT) heldKarp(dist, n, returnToEnd) else heuristic(dist, n, returnToEnd)
        return order.map { it - 1 }
    }

    /** Total length of a path start → points in [order] (matrix indices) → (end). */
    fun pathLength(dist: Array<DoubleArray>, order: List<Int>, n: Int, returnToEnd: Boolean): Double {
        var total = 0.0
        var prev = 0
        for (i in order) {
            total += dist[prev][i]
            prev = i
        }
        if (returnToEnd) total += dist[prev][n + 1]
        return total
    }

    // ---- exact ---------------------------------------------------------------------------

    private fun heldKarp(dist: Array<DoubleArray>, n: Int, returnToEnd: Boolean): List<Int> {
        val full = (1 shl n) - 1
        // cost[mask][j]: shortest path from start visiting exactly `mask`, ending at point j.
        val cost = Array(1 shl n) { DoubleArray(n) { Double.POSITIVE_INFINITY } }
        val parent = Array(1 shl n) { IntArray(n) { -1 } }
        for (j in 0 until n) cost[1 shl j][j] = dist[0][j + 1]

        for (mask in 1..full) {
            for (j in 0 until n) {
                if (mask and (1 shl j) == 0) continue
                val c = cost[mask][j]
                if (c == Double.POSITIVE_INFINITY) continue
                for (k in 0 until n) {
                    if (mask and (1 shl k) != 0) continue
                    val next = mask or (1 shl k)
                    val candidate = c + dist[j + 1][k + 1]
                    if (candidate < cost[next][k]) {
                        cost[next][k] = candidate
                        parent[next][k] = j
                    }
                }
            }
        }

        var best = Double.POSITIVE_INFINITY
        var last = 0
        for (j in 0 until n) {
            val total = cost[full][j] + if (returnToEnd) dist[j + 1][n + 1] else 0.0
            if (total < best) {
                best = total
                last = j
            }
        }

        val order = ArrayList<Int>(n)
        var mask = full
        var j = last
        while (j >= 0) {
            order += j + 1
            val p = parent[mask][j]
            mask = mask and (1 shl j).inv()
            j = p
        }
        return order.reversed()
    }

    // ---- heuristic -----------------------------------------------------------------------

    private fun heuristic(dist: Array<DoubleArray>, n: Int, returnToEnd: Boolean): List<Int> {
        val order = nearestNeighbour(dist, n).toMutableList()
        var improved = true
        while (improved) {
            improved = twoOpt(dist, order, n, returnToEnd) or orOpt(dist, order, n, returnToEnd)
        }
        return order
    }

    private fun nearestNeighbour(dist: Array<DoubleArray>, n: Int): List<Int> {
        val unvisited = (1..n).toMutableSet()
        val order = ArrayList<Int>(n)
        var current = 0
        while (unvisited.isNotEmpty()) {
            val next = unvisited.minBy { dist[current][it] }
            order += next
            unvisited -= next
            current = next
        }
        return order
    }

    /** Matrix index of the node before position i in the full path (start counts as -1). */
    private fun nodeAt(order: List<Int>, i: Int, n: Int, returnToEnd: Boolean): Int = when {
        i < 0 -> 0
        i >= order.size -> if (returnToEnd) n + 1 else -1
        else -> order[i]
    }

    private fun edge(dist: Array<DoubleArray>, a: Int, b: Int): Double = if (a < 0 || b < 0) 0.0 else dist[a][b]

    /** Reverse a segment when doing so shortens the path. Start and end stay fixed. */
    private fun twoOpt(dist: Array<DoubleArray>, order: MutableList<Int>, n: Int, returnToEnd: Boolean): Boolean {
        var improved = false
        for (i in 0 until order.size - 1) {
            for (k in i + 1 until order.size) {
                val a = nodeAt(order, i - 1, n, returnToEnd)
                val b = order[i]
                val c = order[k]
                val d = nodeAt(order, k + 1, n, returnToEnd)
                val delta = edge(dist, a, c) + edge(dist, b, d) - edge(dist, a, b) - edge(dist, c, d)
                if (delta < -1e-9) {
                    order.subList(i, k + 1).reverse()
                    improved = true
                }
            }
        }
        return improved
    }

    /** Move a run of 1–3 points to a better position elsewhere in the path. */
    private fun orOpt(dist: Array<DoubleArray>, order: MutableList<Int>, n: Int, returnToEnd: Boolean): Boolean {
        for (len in 1..3) {
            for (i in 0..order.size - len) {
                val before = pathLength(dist, order, n, returnToEnd)
                val segment = order.subList(i, i + len).toList()
                val rest = order.toMutableList().also { repeat(len) { _ -> it.removeAt(i) } }
                for (j in 0..rest.size) {
                    if (j == i) continue
                    for (seg in listOf(segment, segment.reversed())) {
                        val candidate = rest.toMutableList().also { it.addAll(j, seg) }
                        if (pathLength(dist, candidate, n, returnToEnd) < before - 1e-9) {
                            order.clear()
                            order.addAll(candidate)
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}
