package com.gymapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RutinasTest {

    @Test fun `rutinas guia tienen las zonas esperadas`() {
        assertEquals(7, Catalogo.crearRutina(GrupoMuscular.PECHO).ejercicios.size)
        assertEquals(9, Catalogo.crearRutina(GrupoMuscular.ESPALDA).ejercicios.size)
        assertEquals(7, Catalogo.crearRutina(GrupoMuscular.TREN_INFERIOR).ejercicios.size)
    }

    @Test fun `series iniciales respetan el maximo orientativo`() {
        val pecho = Catalogo.crearRutina(GrupoMuscular.PECHO)
        assertEquals(3, pecho.ejercicios[0].series.size)   // Pecho superior: 3
        assertEquals(3, pecho.ejercicios[2].series.size)   // Pecho inferior: 2–3
        val inferior = Catalogo.crearRutina(GrupoMuscular.TREN_INFERIOR)
        assertEquals(4, inferior.ejercicios[0].series.size) // Cuádriceps: 3–4
        assertEquals(2, inferior.ejercicios[5].series.size) // Tibial anterior: 2
    }

    @Test fun `cada rutina guia es una copia independiente`() {
        val a = Catalogo.crearRutina(GrupoMuscular.PECHO)
        val b = Catalogo.crearRutina(GrupoMuscular.PECHO)
        assertNotSame(a, b)
        a.ejercicios[0].marcarSerie(0, true)
        assertFalse(b.ejercicios[0].series[0].completada)
    }

    @Test fun `editar series y repeticiones`() {
        val e = Catalogo.crearRutina(GrupoMuscular.PECHO).ejercicios[0]
        assertTrue(e.agregarSerie(12).esOk)
        assertEquals(4, e.series.size)
        assertTrue(e.eliminarSerie(0).esOk)
        assertEquals(3, e.series.size)
        assertTrue(e.cambiarRepeticiones(0, 15).esOk)
        assertEquals(15, e.series[0].repeticiones)
        assertFalse(e.cambiarRepeticiones(0, 0).esOk)
        assertFalse(e.cambiarRepeticiones(0, -3).esOk)
        assertFalse(e.eliminarSerie(99).esOk)
    }

    @Test fun `marcar completado en cualquier orden y progreso`() {
        val r = Rutina("1", "Mi rutina", TipoRutina.PERSONALIZADA)
        r.agregarEjercicio("Press banca", 2, 10)
        r.agregarEjercicio("Fondos", 2, 8)
        assertEquals(0.0, r.progreso)
        r.ejercicios[1].marcarSerie(1, true)   // empieza por el segundo ejercicio
        r.ejercicios[0].marcarSerie(0, true)
        assertEquals(0.5, r.progreso)
        assertFalse(r.completada)
        r.ejercicios[0].marcarSerie(1, true)
        r.ejercicios[1].marcarSerie(0, true)
        assertTrue(r.completada)
        r.reiniciarProgreso()
        assertEquals(0.0, r.progreso)
    }

    @Test fun `ejercicio con datos invalidos es rechazado`() {
        val r = Rutina("1", "X", TipoRutina.PERSONALIZADA)
        assertFalse(r.agregarEjercicio("  ", 3, 10).esOk)
        assertFalse(r.agregarEjercicio("Sentadilla", 0, 10).esOk)
        assertFalse(r.agregarEjercicio("Sentadilla", 3, -1).esOk)
        assertTrue(r.ejercicios.isEmpty())
    }

    @Test fun `flujo de registro completo`() {
        val repo = UsuarioRepositorioMemoria()
        val reg = RegistroUsuario()
        assertFalse(reg.establecerNombre("").esOk)
        assertFalse(reg.establecerPeso("abc", UnidadPeso.KG).esOk)
        assertFalse(reg.puedeFinalizar)
        assertFalse(reg.finalizar(repo).esOk)

        assertTrue(reg.establecerNombre("kenedy").esOk)
        assertTrue(reg.establecerPeso("70", UnidadPeso.KG).esOk)
        assertTrue(reg.establecerAltura("175").esOk)
        reg.establecerNivel(NivelExperiencia.PRINCIPIANTE)
        assertTrue(reg.puedeFinalizar)

        val usuario = reg.finalizar(repo).obtenerONull()
        assertNotNull(usuario)
        assertEquals("kenedy", repo.obtener()?.nombre)
    }

    @Test fun `modo personalizado crear listar y eliminar`() {
        val gestor = GestorRutinasPersonalizadas(RutinaRepositorioMemoria())
        assertTrue(gestor.listar().isEmpty())

        val rutina = gestor.crear("Push day").obtenerONull()
        assertNotNull(rutina)
        assertFalse(gestor.crear("push day").esOk)   // duplicado
        assertFalse(gestor.crear("   ").esOk)        // vacío

        rutina.agregarEjercicio("Press militar", 4, 8)
        gestor.guardar(rutina)
        assertEquals(1, gestor.listar().size)
        assertEquals(4, gestor.listar()[0].ejercicios[0].series.size)

        gestor.eliminar(rutina.id)
        assertTrue(gestor.listar().isEmpty())
        assertNull(RutinaRepositorioMemoria().obtener("nada"))
    }
}
