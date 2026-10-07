package com.hoodie.app.pixel.icons

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelIconCatalogTest {
    private fun check(name: String, s: PixelSprite) {
        assertTrue("$name: grade vazia", s.rows.isNotEmpty())
        s.rows.forEachIndexed { i, row ->
            assertEquals("$name: linha $i com largura diferente", s.width, row.length)
            assertTrue("$name: caractere inválido em '$row'", row.all { it in "#o." })
        }
        assertTrue("$name: sem nenhum pixel", s.rows.any { r -> r.any { it != '.' } })
    }

    @Test
    fun `own sprites are rectangular 10x10 and use only the legend`() {
        PixelIcons.OWN.forEachIndexed { i, s ->
            check("própria #$i", s)
            assertEquals(10, s.width)
            assertEquals(10, s.height)
        }
    }

    @Test
    fun `every place type context and period has an icon`() {
        PlaceType.entries.forEach { check("lugar ${it.name}", PixelIcons.of(it)) }
        UserContextType.entries.forEach { check("contexto ${it.name}", PixelIcons.of(it)) }
        DayPeriod.entries.forEach { check("período ${it.name}", PixelIcons.of(it)) }
    }

    @Test
    fun `place icons are all the same size so lists line up`() {
        assertEquals(1, PlaceType.entries.map { PixelIcons.of(it).width to PixelIcons.of(it).height }.toSet().size)
    }
}
