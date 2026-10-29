package com.gymapp.core

import java.time.LocalDate

/**
 * Apartado de estadísticas: guarda las sesiones terminadas (usando Historial)
 * y la duración de cada una.
 */
class Informe(private val historial: Historial) {

    private val duraciones = mutableMapOf<String, Long>()

    /** Guarda una foto de la rutina tal como quedó. Devuelve error si no hay series completadas. */
    fun guardar(rutina: Rutina, segundos: Long): Resultado<RegistroSesion> {
        val sesion = RegistroSesion.desde(rutina)
        val resultado = historial.registrar(sesion)
        if (resultado.esOk) {
            duraciones[sesion.id] = segundos
            // Historial reemplaza la sesión del mismo día y rutina; limpiamos duraciones huérfanas.
            val vigentes = historial.listar().map { it.id }.toSet()
            duraciones.keys.retainAll(vigentes)
        }
        return resultado
    }

    fun sesiones(): List<RegistroSesion> = historial.listar()

    fun duracion(sesion: RegistroSesion): Long = duraciones[sesion.id] ?: 0L

    fun eliminar(sesion: RegistroSesion) {
        historial.eliminar(sesion.id)
        duraciones.remove(sesion.id)
    }

    fun comparar(periodo: Periodo, unidad: UnidadPeso): ResumenAvance =
        historial.comparar(periodo, LocalDate.now(), null, unidad)

    fun seriesHechas(s: RegistroSesion): Int =
        s.ejercicios.sumOf { e -> e.series.count { it.completada } }

    fun seriesTotales(s: RegistroSesion): Int =
        s.ejercicios.sumOf { e -> e.series.size }

    fun volumen(s: RegistroSesion): Double =
        s.ejercicios.sumOf { e ->
            e.series.filter { it.completada }.sumOf { it.repeticiones * (it.peso ?: 0.0) }
        }

    fun tiempoTotal(): Long = sesiones().sumOf { duracion(it) }
    fun volumenTotal(): Double = sesiones().sumOf { volumen(it) }
    fun seriesTotalesHechas(): Int = sesiones().sumOf { seriesHechas(it) }
}