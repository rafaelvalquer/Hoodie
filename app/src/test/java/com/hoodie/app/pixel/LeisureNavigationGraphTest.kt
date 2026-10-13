package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.leisure.LeisureNavigationGraph
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Rotas, limites e obstáculos do Passeio (P0 — navegação). */
class LeisureNavigationGraphTest {
    private val variants = 0..2

    @Test fun everyPairOfSpotsIsConnected() {
        LeisureNpcSpot.entries.forEach { a ->
            LeisureNpcSpot.entries.forEach { b ->
                val route = LeisureNavigationGraph.route(a, b)
                assertEquals(a, route.first()); assertEquals(b, route.last())
                route.zipWithNext().forEach { (x, y) ->
                    assertTrue("$x–$y não é aresta", LeisureNavigationGraph.allEdges.any { (p, q) -> (p == x && q == y) || (p == y && q == x) })
                }
            }
        }
    }

    @Test fun noSpotSitsInsideAnObstacle() {
        variants.forEach { v ->
            LeisureNpcSpot.entries.filter { it != LeisureNpcSpot.BENCH_SEAT }.forEach { id ->
                val s = LeisureNavigationGraph.spot(v, id)
                LeisureNavigationGraph.obstacles(v).forEach { o ->
                    assertFalse("variante $v: $id dentro de ${o.name}", o.contains(s.x, s.floorY))
                }
            }
        }
    }

    /** Cada aresta, amostrada pixel a pixel, não atravessa lago, chafariz, banco, mirante nem poste. */
    @Test fun edgesNeverCrossObstacles() {
        variants.forEach { v ->
            LeisureNavigationGraph.allEdges.forEach { (a, b) ->
                if (LeisureNavigationGraph.isBenchAccess(a, b)) return@forEach
                val p = LeisureNavigationGraph.spot(v, a); val q = LeisureNavigationGraph.spot(v, b)
                val n = max(abs(q.x - p.x), abs(q.floorY - p.floorY)).coerceAtLeast(1)
                (0..n).forEach { i ->
                    val x = p.x + (q.x - p.x) * i / n; val y = p.floorY + (q.floorY - p.floorY) * i / n
                    LeisureNavigationGraph.obstacles(v).forEach { o ->
                        assertFalse("variante $v: $a–$b atravessa ${o.name} em ($x,$y)", o.contains(x, y))
                    }
                }
            }
        }
    }

    /** A única entrada na área do banco é o passo da frente dele até o assento. */
    @Test fun theBenchIsOnlyReachedFromItsFront() {
        val benchEdges = LeisureNavigationGraph.allEdges.filter { (a, b) -> LeisureNpcSpot.BENCH_SEAT in setOf(a, b) }
        assertEquals(listOf(LeisureNpcSpot.BENCH_APPROACH to LeisureNpcSpot.BENCH_SEAT), benchEdges)
        variants.forEach { v ->
            val seat = LeisureNavigationGraph.spot(v, LeisureNpcSpot.BENCH_SEAT)
            val front = LeisureNavigationGraph.spot(v, LeisureNpcSpot.BENCH_APPROACH)
            assertEquals("passo vertical, sem cruzar as pernas do banco", seat.x, front.x)
        }
    }

    /** Pontos de parada ficam fora das faixas que o Hoodie usa no caminho (x 52, 120 e 188 ± 12). */
    @Test fun stopsDoNotOverlapTheHoodieOnThePath() {
        val hoodieXs = listOf(52, 120, 188)
        val stops = listOf(LeisureNpcSpot.PATH_LEFT, LeisureNpcSpot.PATH_CENTER, LeisureNpcSpot.PATH_RIGHT, LeisureNpcSpot.SOCIAL_LEFT, LeisureNpcSpot.SOCIAL_RIGHT)
        variants.forEach { v ->
            stops.forEach { id ->
                val s = LeisureNavigationGraph.spot(v, id)
                hoodieXs.forEach { hx -> assertTrue("$id em x ${s.x} encosta no Hoodie em $hx", abs(s.x - hx) >= 18) }
            }
        }
    }

    @Test fun entrancesAreOffscreen() {
        variants.forEach { v ->
            assertTrue(LeisureNavigationGraph.spot(v, LeisureNpcSpot.ENTRANCE_LEFT).x <= -24)
            assertTrue(LeisureNavigationGraph.spot(v, LeisureNpcSpot.ENTRANCE_RIGHT).x >= 264)
        }
    }
}
