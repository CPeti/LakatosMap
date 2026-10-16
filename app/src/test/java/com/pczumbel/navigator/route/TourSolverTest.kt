package com.pczumbel.navigator.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class TourSolverTest {

    /** Matrix over start, points, end on a plane — easy to reason about by hand. */
    private fun matrix(start: Pair<Double, Double>, points: List<Pair<Double, Double>>, end: Pair<Double, Double>): Array<DoubleArray> {
        val all = listOf(start) + points + listOf(end)
        return Array(all.size) { i -> DoubleArray(all.size) { j -> hypot(all[i].first - all[j].first, all[i].second - all[j].second) } }
    }

    @Test
    fun pointsOnALineAreVisitedInOrder() {
        val pts = listOf(3.0 to 0.0, 1.0 to 0.0, 4.0 to 0.0, 2.0 to 0.0)
        val m = matrix(0.0 to 0.0, pts, 0.0 to 0.0)
        assertEquals(listOf(1, 3, 0, 2), TourSolver.solve(m, pts.size, returnToEnd = false))
    }

    @Test
    fun returningToStartChangesTheBestOrder() {
        // Open path: go right then far right. Loop: a square, visited around the perimeter.
        val pts = listOf(1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        val m = matrix(0.0 to 0.0, pts, 0.0 to 0.0)
        val order = TourSolver.solve(m, pts.size, returnToEnd = true)
        assertEquals(4.0, TourSolver.pathLength(m, order.map { it + 1 }, pts.size, true), 1e-9)
    }

    @Test
    fun heuristicIsCloseToExactOnRandomInstances() {
        // Held–Karp is the oracle: on 12 random points the heuristic must be within 10 %.
        val rnd = Random(42)
        repeat(20) {
            val pts = List(TourSolver.EXACT_LIMIT) { rnd.nextDouble() to rnd.nextDouble() }
            val m = matrix(0.5 to 0.5, pts, 0.5 to 0.5)
            val exact = TourSolver.solve(m, pts.size, returnToEnd = true)
            val exactLen = TourSolver.pathLength(m, exact.map { it + 1 }, pts.size, true)

            // Force the heuristic path by solving a 13-point instance with a duplicated point.
            val pts13 = pts + pts.last()
            val m13 = matrix(0.5 to 0.5, pts13, 0.5 to 0.5)
            val heur = TourSolver.solve(m13, pts13.size, returnToEnd = true)
            val heurLen = TourSolver.pathLength(m13, heur.map { it + 1 }, pts13.size, true)

            assertTrue("heuristic $heurLen vs exact $exactLen", heurLen <= exactLen * 1.10 + 1e-9)
        }
    }

    @Test
    fun everyPointVisitedExactlyOnce() {
        val rnd = Random(7)
        val pts = List(30) { rnd.nextDouble() * 10 to rnd.nextDouble() * 10 }
        val m = matrix(0.0 to 0.0, pts, 0.0 to 0.0)
        val order = TourSolver.solve(m, pts.size, returnToEnd = false)
        assertEquals((0 until 30).toList(), order.sorted())
    }
}
