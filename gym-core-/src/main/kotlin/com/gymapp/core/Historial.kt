package com.gymapp.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.abs

// =====================================================================
//  PERIODOS: día a día, semana a semana, mes a mes, año a año
// =====================================================================

enum class Periodo(val titulo: String, val etiquetaActual: String, val etiquetaAnterior: String) {
    DIA("Día a día", "hoy", "ayer"),
    SEMANA("Semana a semana", "esta semana", "la semana pasada"),
    MES("Mes a mes", "este mes", "el mes pasado"),
    ANIO("Año a año", "este año", "el año pasado")
}

data class RangoFechas(val desde: LocalDate, val hasta: LocalDate) {
    operator fun contains(fecha: LocalDate): Boolean = !fecha.isBefore(desde) && !fecha.isAfter(hasta)
}

/** Rango (día, semana lunes-domingo, mes o año calendario) que contiene la fecha dada. */
fun Periodo.rangoDe(fecha: LocalDate): RangoFechas = when (this) {
    Periodo.DIA -> RangoFechas(fecha, fecha)
    Periodo.SEMANA -> {
        val lunes = fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        RangoFechas(lunes, lunes.plusDays(6))
    }
    Periodo.MES -> RangoFechas(fecha.withDayOfMonth(1), fecha.with(TemporalAdjusters.lastDayOfMonth()))
    Periodo.ANIO -> RangoFechas(fecha.withDayOfYear(1), fecha.with(TemporalAdjusters.lastDayOfYear()))
}

/** El periodo inmediatamente anterior al que contiene la fecha. */
fun Periodo.rangoAnteriorA(fecha: LocalDate): RangoFechas = rangoDe(rangoDe(fecha).desde.minusDays(1))

// =====================================================================
//  REGISTROS: "foto" de un entrenamiento terminado
// =====================================================================

data class SerieRegistrada(val repeticiones: Int, val peso: Double?, val completada: Boolean)

data class EjercicioRegistrado(val nombre: String, val series: List<SerieRegistrada>)

data class RegistroSesion(
    val id: String,
    val fecha: LocalDate,
    val rutinaId: String,
    val rutinaNombre: String,
    val ejercicios: List<EjercicioRegistrado>
) {
    companion object {
        /** Toma una foto de la rutina tal como está ahora (pesos, repeticiones y casillas marcadas). */
        fun desde(
            rutina: Rutina,
            fecha: LocalDate = LocalDate.now(),
            id: String = UUID.randomUUID().toString()
        ): RegistroSesion = RegistroSesion(
            id = id,
            fecha = fecha,
            rutinaId = rutina.id,
            rutinaNombre = rutina.nombre,
            ejercicios = rutina.ejercicios.map { e ->
                EjercicioRegistrado(
                    e.nombre,
                    e.series.map { s -> SerieRegistrada(s.repeticiones, s.peso, s.completada) }
                )
            }
        )
    }
}

// =====================================================================
//  MÉTRICAS Y COMPARACIÓN
// =====================================================================

/** Resumen de lo trabajado. Solo cuentan las series marcadas como completadas. */
data class Metricas(
    val volumen: Double,        // suma de peso x repeticiones
    val pesoMaximo: Double?,    // el peso más alto usado (null si no se registró peso)
    val repeticiones: Int,
    val series: Int
) {
    companion object {
        /** null si no hubo ninguna serie completada. */
        fun desde(ejercicios: List<EjercicioRegistrado>): Metricas? {
            var volumen = 0.0
            var pesoMax: Double? = null
            var reps = 0
            var series = 0
            for (s in ejercicios.flatMap { it.series }) {
                if (!s.completada) continue
                series++
                reps += s.repeticiones
                val p = s.peso
                if (p != null) {
                    volumen += p * s.repeticiones
                    pesoMax = maxOf(pesoMax ?: p, p)
                }
            }
            return if (series == 0) null else Metricas(volumen, pesoMax, reps, series)
        }
    }
}

enum class Tendencia {
    SUBIO,       // mejoró frente al periodo anterior
    BAJO,        // bajó frente al periodo anterior
    IGUAL,       // se mantuvo
    NUEVO,       // hay datos ahora, pero no en el periodo anterior
    SIN_DATOS    // no hay datos en el periodo actual
}

data class ComparacionEjercicio(
    val nombre: String,
    val actual: Metricas?,
    val anterior: Metricas?,
    val tendencia: Tendencia,
    val variacionPorcentaje: Double?,
    val mensaje: String
)

data class ResumenAvance(
    val periodo: Periodo,
    val rangoActual: RangoFechas,
    val rangoAnterior: RangoFechas,
    val sesionesActual: Int,
    val sesionesAnterior: Int,
    val volumenActual: Double,
    val volumenAnterior: Double,
    val tendencia: Tendencia,
    val variacionPorcentaje: Double?,
    val mensaje: String,
    val comparaciones: List<ComparacionEjercicio>
)

// =====================================================================
//  HISTORIAL
// =====================================================================

class Historial(private val repositorio: HistorialRepositorio) {

    /**
     * Guarda un entrenamiento terminado.
     * Si ya existía uno de la MISMA rutina en la MISMA fecha, se reemplaza (no se duplica).
     */
    fun registrar(sesion: RegistroSesion): Resultado<RegistroSesion> {
        val hayAvance = sesion.ejercicios.any { e -> e.series.any { it.completada } }
        if (!hayAvance) {
            return Resultado.Error("Marca al menos una serie como completada para guardar tu avance.")
        }
        repositorio.listar()
            .filter { it.rutinaId == sesion.rutinaId && it.fecha == sesion.fecha && it.id != sesion.id }
            .forEach { repositorio.eliminar(it.id) }
        repositorio.guardar(sesion)
        return Resultado.Ok(sesion)
    }

    /** Todos los registros (más recientes primero). Con rutinaId, solo los de esa rutina. */
    fun listar(rutinaId: String? = null): List<RegistroSesion> =
        repositorio.listar()
            .filter { rutinaId == null || it.rutinaId == rutinaId }
            .sortedByDescending { it.fecha }

    fun eliminar(id: String) = repositorio.eliminar(id)

    /**
     * Compara el periodo que contiene [fechaReferencia] contra el periodo anterior.
     * [unidad] solo sirve para escribir los mensajes (kg o lb).
     */
    fun comparar(
        periodo: Periodo,
        fechaReferencia: LocalDate = LocalDate.now(),
        rutinaId: String? = null,
        unidad: UnidadPeso = UnidadPeso.KG
    ): ResumenAvance {
        val rActual = periodo.rangoDe(fechaReferencia)
        val rAnterior = periodo.rangoAnteriorA(fechaReferencia)

        val todas = listar(rutinaId)
        val sActual = todas.filter { it.fecha in rActual }
        val sAnterior = todas.filter { it.fecha in rAnterior }

        val gActual = agrupar(sActual)
        val gAnterior = agrupar(sAnterior)

        val comparaciones = (gActual.keys + gAnterior.keys).map { clave ->
            val nombre = (gActual[clave] ?: gAnterior.getValue(clave)).nombre
            compararEjercicio(
                nombre,
                gActual[clave]?.let { Metricas.desde(it.ejercicios) },
                gAnterior[clave]?.let { Metricas.desde(it.ejercicios) },
                periodo,
                unidad
            )
        }

        val totalActual = Metricas.desde(sActual.flatMap { it.ejercicios })
        val totalAnterior = Metricas.desde(sAnterior.flatMap { it.ejercicios })
        val general = resumenGeneral(totalActual, totalAnterior, periodo, unidad)

        return ResumenAvance(
            periodo = periodo,
            rangoActual = rActual,
            rangoAnterior = rAnterior,
            sesionesActual = sActual.size,
            sesionesAnterior = sAnterior.size,
            volumenActual = totalActual?.volumen ?: 0.0,
            volumenAnterior = totalAnterior?.volumen ?: 0.0,
            tendencia = general.tendencia,
            variacionPorcentaje = general.variacion,
            mensaje = general.mensaje,
            comparaciones = comparaciones
        )
    }
}

// ---------------------------------------------------------------------
//  Funciones internas
// ---------------------------------------------------------------------

private class Grupo(val nombre: String, val ejercicios: MutableList<EjercicioRegistrado> = mutableListOf())

/** Junta los ejercicios con el mismo nombre (sin importar mayúsculas ni espacios sobrantes). */
private fun agrupar(sesiones: List<RegistroSesion>): Map<String, Grupo> {
    val mapa = linkedMapOf<String, Grupo>()
    for (sesion in sesiones.sortedBy { it.fecha }) {
        for (e in sesion.ejercicios) {
            val clave = e.nombre.trim().lowercase()
            mapa.getOrPut(clave) { Grupo(e.nombre.trim()) }.ejercicios.add(e)
        }
    }
    return mapa
}

private class MetricaElegida(val actual: Double, val anterior: Double, val nombre: String, val unidad: String)

/** Usa el volumen (peso x reps) si ambos periodos tienen peso; si no, compara repeticiones. */
private fun elegirMetrica(a: Metricas, b: Metricas, unidad: UnidadPeso): MetricaElegida =
    if (a.volumen > 0.0 && b.volumen > 0.0) {
        MetricaElegida(a.volumen, b.volumen, "volumen", unidad.etiqueta)
    } else {
        MetricaElegida(a.repeticiones.toDouble(), b.repeticiones.toDouble(), "repeticiones", "reps")
    }

private fun tendenciaDe(actual: Double, anterior: Double): Tendencia = when {
    actual - anterior > 1e-9 -> Tendencia.SUBIO
    anterior - actual > 1e-9 -> Tendencia.BAJO
    else -> Tendencia.IGUAL
}

private fun variacionPct(actual: Double, anterior: Double): Double? =
    if (anterior > 0.0) (actual - anterior) / anterior * 100.0 else null

/** Número sin decimales sobrantes: 60.0 -> "60", 62.5 -> "62.5" */
internal fun fmt(x: Double): String {
    val r = Math.round(x * 10.0) / 10.0
    return if (r == Math.rint(r)) r.toLong().toString() else r.toString()
}

private fun compararEjercicio(
    nombre: String,
    act: Metricas?,
    ant: Metricas?,
    periodo: Periodo,
    unidad: UnidadPeso
): ComparacionEjercicio {
    if (act == null) {
        return ComparacionEjercicio(
            nombre, null, ant, Tendencia.SIN_DATOS, null,
            "$nombre: no lo entrenaste ${periodo.etiquetaActual}."
        )
    }
    if (ant == null) {
        return ComparacionEjercicio(
            nombre, act, null, Tendencia.NUEVO, null,
            "$nombre: primer registro ${periodo.etiquetaActual}, aún no hay con qué comparar."
        )
    }

    val m = elegirMetrica(act, ant, unidad)
    val tendencia = tendenciaDe(m.actual, m.anterior)
    val pct = variacionPct(m.actual, m.anterior)
    val detalle = "(${fmt(m.anterior)} → ${fmt(m.actual)} ${m.unidad})"

    var mensaje = when (tendencia) {
        Tendencia.SUBIO -> "$nombre: subiste ${fmt(abs(pct ?: 0.0))}% en ${m.nombre} $detalle."
        Tendencia.BAJO -> "$nombre: bajaste ${fmt(abs(pct ?: 0.0))}% en ${m.nombre} $detalle."
        else -> "$nombre: te mantuviste igual en ${m.nombre} $detalle."
    }

    val pesoActual = act.pesoMaximo
    val pesoAnterior = ant.pesoMaximo
    if (pesoActual != null && pesoAnterior != null && pesoActual != pesoAnterior) {
        mensaje += " Peso máximo: ${fmt(pesoAnterior)} → ${fmt(pesoActual)} ${unidad.etiqueta}."
    }
    return ComparacionEjercicio(nombre, act, ant, tendencia, pct, mensaje)
}

private class General(val tendencia: Tendencia, val variacion: Double?, val mensaje: String)

private fun resumenGeneral(
    actual: Metricas?,
    anterior: Metricas?,
    periodo: Periodo,
    unidad: UnidadPeso
): General {
    if (actual == null) {
        return General(
            Tendencia.SIN_DATOS, null,
            "No hay entrenamientos con series completadas ${periodo.etiquetaActual}."
        )
    }
    if (anterior == null) {
        return General(
            Tendencia.NUEVO, null,
            "Primer registro ${periodo.etiquetaActual}: aún no hay datos de ${periodo.etiquetaAnterior} para comparar. ¡Sigue así!"
        )
    }
    val m = elegirMetrica(actual, anterior, unidad)
    val tendencia = tendenciaDe(m.actual, m.anterior)
    val pct = variacionPct(m.actual, m.anterior)
    val detalle = "(${fmt(m.anterior)} → ${fmt(m.actual)} ${m.unidad})"
    val mensaje = when (tendencia) {
        Tendencia.SUBIO ->
            "¡Hubo avance! Frente a ${periodo.etiquetaAnterior}, tu ${m.nombre} total subió ${fmt(abs(pct ?: 0.0))}% $detalle."
        Tendencia.BAJO ->
            "Frente a ${periodo.etiquetaAnterior}, tu ${m.nombre} total bajó ${fmt(abs(pct ?: 0.0))}% $detalle. ¡Ánimo, retoma el ritmo!"
        else ->
            "Te mantuviste igual que ${periodo.etiquetaAnterior} en ${m.nombre} total $detalle."
    }
    return General(tendencia, pct, mensaje)
}