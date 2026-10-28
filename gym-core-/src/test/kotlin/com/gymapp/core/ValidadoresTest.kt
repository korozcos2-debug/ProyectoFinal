package com.gymapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidadoresTest {

    @Test fun `nombre valido`() {
        assertEquals("kenedy", Validadores.nombreUsuario("  kenedy ").obtenerONull())
    }

    @Test fun `nombre invalido`() {
        listOf("", "   ", "ab", "ken edy", "1kenedy", "ken@dy", "a".repeat(21)).forEach {
            assertTrue(Validadores.nombreUsuario(it) is Resultado.Error, "Debía fallar: '$it'")
        }
    }

    @Test fun `peso valido en kg y lb`() {
        assertEquals(70.0, Validadores.peso("70", UnidadPeso.KG).obtenerONull())
        assertEquals(72.5, Validadores.peso("72,5", UnidadPeso.KG).obtenerONull())
        assertEquals(154.0, Validadores.peso("154", UnidadPeso.LB).obtenerONull())
    }

    @Test fun `peso invalido`() {
        listOf("", "abc", "-70", "0", "70kg", "7 0", "1e3", "5", "999").forEach {
            assertTrue(Validadores.peso(it, UnidadPeso.KG) is Resultado.Error, "Debía fallar: '$it'")
        }
    }

    @Test fun `altura valida e invalida`() {
        assertEquals(175.0, Validadores.altura("175").obtenerONull())
        listOf("", "-175", "alto", "175cm", "0", "10", "400").forEach {
            assertTrue(Validadores.altura(it) is Resultado.Error, "Debía fallar: '$it'")
        }
    }

    @Test fun `conversion de libras a kg`() {
        val u = Usuario("kenedy", 154.0, UnidadPeso.LB, 175.0, NivelExperiencia.PRINCIPIANTE)
        assertEquals(69.85, u.pesoKg, 0.01)
    }
}
