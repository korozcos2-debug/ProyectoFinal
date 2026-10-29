package com.gymapp.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Apartado de estadísticas. Guarda TODAS las sesiones terminadas (varias por día)
 * junto con su duración y hora. La fecha sale del reloj del sistema (en Android, el del teléfono).
 */
class Informe(
    private val repositorio: HistorialRepositorio,
    private val reloj: Clock = Clock.systemDefaultZone()
) {
    private val historial = Historial(repositorio) // solo se usa para comparar()

    private class Extra(val segundos: Long, val hora: LocalTime)

    private val extras = mutableMapOf<String, Extra>()

    /** Guarda la sesión sin reemplazar las anteriores del mismo día. */
    fun guardar(rutina: Rutina, segundos: Long): Resultado<RegistroSesion> {
        val sesion = RegistroSesion.desde(rutina, LocalDate.now(reloj))
        val hayAvance = sesion.ejercicios.any { e -> e.series.any { it.completada } }
        if (!hayAvance) {
            return Resultado.Error("Marca al menos una serie como completada para guardar tu avance.")
        }
        repositorio.guardar(sesion)
        extras[sesion.id] = Extra(segundos, LocalTime.now(reloj).withNano(0))
        return Resultado.Ok(sesion)
    }

    fun sesiones(): List<RegistroSesion> = historial.listar()

    /** Sesiones agrupadas por día (el más reciente primero); dentro del día, por hora. */
    fun sesionesPorDia(): List<Pair<LocalDate, List<RegistroSesion>>> =
        sesiones().groupBy { it.fecha }
            .map { (fecha, lista) -> fecha to lista.sortedBy { hora(it) ?: LocalTime.MIN } }

    /** Misma numeración que se muestra en pantalla. */
    fun sesionesPlanas(): List<RegistroSesion> = sesionesPorDia().flatMap { it.second }

    fun duracion(sesion: RegistroSesion): Long = extras[sesion.id]?.segundos ?: 0L

    fun hora(sesion: RegistroSesion): LocalTime? = extras[sesion.id]?.hora

    fun eliminar(sesion: RegistroSesion) {
        repositorio.eliminar(sesion.id)
        extras.remove(sesion.id)
    }

    fun comparar(periodo: Periodo, unidad: UnidadPeso): ResumenAvance =
        historial.comparar(periodo, LocalDate.now(reloj), null, unidad)

    fun seriesHechas(s: RegistroSesion): Int =
        s.ejercicios.sumOf { e -> e.series.count { it.completada } }

    fun seriesTotales(s: RegistroSesion): Int =
        s.ejercicios.sumOf { e -> e.series.size }

    fun volumen(s: RegistroSesion): Double =
        s.ejercicios.sumOf { e ->
            e.series.filter { it.completada }.sumOf { it.repeticiones * (it.peso ?: 0.0) }
        }
    /** Restaura duración y hora de una sesión guardada antes (al reabrir la app). */
    fun restaurar(sesionId: String, segundos: Long, hora: LocalTime) {
        extras[sesionId] = Extra(segundos, hora)
    }

    /** Duración y hora de todas las sesiones, para guardarlas en el teléfono. */
    fun extrasActuales(): Map<String, Pair<Long, LocalTime>> =
        extras.mapValues { it.value.segundos to it.value.hora }
    fun tiempoTotal(): Long = sesiones().sumOf { duracion(it) }
    fun volumenTotal(): Double = sesiones().sumOf { volumen(it) }
    fun seriesTotalesHechas(): Int = sesiones().sumOf { seriesHechas(it) }
}