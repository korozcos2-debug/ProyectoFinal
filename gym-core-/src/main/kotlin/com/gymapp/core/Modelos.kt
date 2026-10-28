package com.gymapp.core

/** Límites de la app (útiles también para la UI). */
object Limites {
    const val REPS_MIN = 1
    const val REPS_MAX = 100
    const val REPS_POR_DEFECTO = 10
    const val SERIES_MAX = 20
    const val EJERCICIOS_MAX = 30
    const val NOMBRE_TEXTO_MAX = 60
    const val PESO_SERIE_MAX = 1000.0   // peso máximo por serie (en la unidad del usuario)
}

enum class UnidadPeso(val etiqueta: String, val factorAKg: Double) {
    KG("kg", 1.0),
    LB("lb", 0.45359237)
}

enum class NivelExperiencia(val titulo: String, val descripcion: String) {
    PRINCIPIANTE("Principiante", "Menos de 6 meses de experiencia"),
    INTERMEDIO("Intermedio", "Entre 6 meses y 2 años"),
    AVANZADO("Avanzado", "Más de 2 años")
}

enum class GrupoMuscular(val titulo: String) {
    PECHO("Pecho"),
    ESPALDA("Espalda"),
    TREN_INFERIOR("Tren inferior")
}

enum class TipoRutina { PREDEFINIDA, PERSONALIZADA }

data class Usuario(
    val nombre: String,
    val peso: Double,
    val unidadPeso: UnidadPeso,
    val alturaCm: Double,
    val nivel: NivelExperiencia
) {
    val pesoKg: Double get() = peso * unidadPeso.factorAKg
}

/** Una serie: repeticiones, peso (opcional) y si el usuario ya la marcó como completada. */
class Serie(
    repeticionesIniciales: Int,
    completadaInicial: Boolean = false,
    pesoInicial: Double? = null
) {
    var repeticiones: Int = repeticionesIniciales
        private set
    var completada: Boolean = completadaInicial

    /** Peso con el que se trabaja, en la unidad del usuario (kg o lb). null = sin peso registrado. */
    var peso: Double? = pesoInicial
        private set

    fun cambiarRepeticiones(nuevas: Int): Resultado<Int> {
        val r = Validadores.repeticiones(nuevas)
        if (r is Resultado.Ok) repeticiones = r.valor
        return r
    }

    fun cambiarPeso(nuevo: Double?): Resultado<Double?> {
        val r = Validadores.pesoSerie(nuevo)
        if (r is Resultado.Ok) peso = r.valor
        return r
    }
}

/** Un ejercicio (o zona muscular) con su lista de series. */
class Ejercicio(nombre: String, series: List<Serie> = emptyList()) {
    var nombre: String = nombre
        private set

    private val _series = series.toMutableList()
    val series: List<Serie> get() = _series

    val seriesCompletadas: Int get() = _series.count { it.completada }
    val completado: Boolean get() = _series.isNotEmpty() && _series.all { it.completada }

    fun renombrar(nuevo: String): Resultado<String> {
        val r = Validadores.textoNombre(nuevo, "El nombre del ejercicio")
        if (r is Resultado.Ok) nombre = r.valor
        return r
    }

    fun agregarSerie(
        repeticiones: Int = Limites.REPS_POR_DEFECTO,
        peso: Double? = null
    ): Resultado<Serie> {
        if (_series.size >= Limites.SERIES_MAX) {
            return Resultado.Error("Máximo ${Limites.SERIES_MAX} series por ejercicio.")
        }
        val rep = Validadores.repeticiones(repeticiones)
        if (rep is Resultado.Error) return rep
        val pesoOk = Validadores.pesoSerie(peso)
        if (pesoOk is Resultado.Error) return pesoOk
        val serie = Serie((rep as Resultado.Ok).valor, false, (pesoOk as Resultado.Ok).valor)
        _series.add(serie)
        return Resultado.Ok(serie)
    }

    fun eliminarSerie(indice: Int): Resultado<Unit> {
        if (indice !in _series.indices) return Resultado.Error("La serie no existe.")
        _series.removeAt(indice)
        return Resultado.Ok(Unit)
    }

    fun cambiarRepeticiones(indice: Int, nuevas: Int): Resultado<Int> {
        val serie = _series.getOrNull(indice) ?: return Resultado.Error("La serie no existe.")
        return serie.cambiarRepeticiones(nuevas)
    }

    fun cambiarPeso(indice: Int, nuevo: Double?): Resultado<Double?> {
        val serie = _series.getOrNull(indice) ?: return Resultado.Error("La serie no existe.")
        return serie.cambiarPeso(nuevo)
    }

    /** Pone el mismo peso en todas las series (atajo: "hoy trabajo con 60 kg"). */
    fun aplicarPesoATodas(peso: Double?): Resultado<Double?> {
        val r = Validadores.pesoSerie(peso)
        if (r is Resultado.Ok) _series.forEach { it.cambiarPeso(r.valor) }
        return r
    }

    fun marcarSerie(indice: Int, completada: Boolean): Resultado<Unit> {
        val serie = _series.getOrNull(indice) ?: return Resultado.Error("La serie no existe.")
        serie.completada = completada
        return Resultado.Ok(Unit)
    }

    fun reiniciarProgreso() = _series.forEach { it.completada = false }
}

/** Una rutina: predefinida (pecho, espalda, tren inferior) o creada por el usuario. */
class Rutina(
    val id: String,
    nombre: String,
    val tipo: TipoRutina,
    val grupo: GrupoMuscular? = null
) {
    var nombre: String = nombre
        private set

    private val _ejercicios = mutableListOf<Ejercicio>()
    val ejercicios: List<Ejercicio> get() = _ejercicios

    val totalSeries: Int get() = _ejercicios.sumOf { it.series.size }
    val seriesCompletadas: Int get() = _ejercicios.sumOf { it.seriesCompletadas }

    /** Avance de 0.0 a 1.0 (para una barra de progreso). */
    val progreso: Double
        get() = if (totalSeries == 0) 0.0 else seriesCompletadas.toDouble() / totalSeries

    val completada: Boolean get() = _ejercicios.isNotEmpty() && _ejercicios.all { it.completado }

    fun renombrar(nuevo: String): Resultado<String> {
        val r = Validadores.textoNombre(nuevo, "El nombre de la rutina")
        if (r is Resultado.Ok) nombre = r.valor
        return r
    }

    fun agregarEjercicio(
        nombre: String,
        numeroSeries: Int = 3,
        repeticiones: Int = Limites.REPS_POR_DEFECTO,
        peso: Double? = null
    ): Resultado<Ejercicio> {
        if (_ejercicios.size >= Limites.EJERCICIOS_MAX) {
            return Resultado.Error("Máximo ${Limites.EJERCICIOS_MAX} ejercicios por rutina.")
        }
        val nombreOk = when (val r = Validadores.textoNombre(nombre, "El nombre del ejercicio")) {
            is Resultado.Ok -> r.valor
            is Resultado.Error -> return r
        }
        if (numeroSeries !in 1..Limites.SERIES_MAX) {
            return Resultado.Error("Las series deben estar entre 1 y ${Limites.SERIES_MAX}.")
        }
        val rep = Validadores.repeticiones(repeticiones)
        if (rep is Resultado.Error) return rep

        val pesoOk = Validadores.pesoSerie(peso)
        if (pesoOk is Resultado.Error) return pesoOk

        val ejercicio = Ejercicio(nombreOk)
        repeat(numeroSeries) { ejercicio.agregarSerie(repeticiones, (pesoOk as Resultado.Ok).valor) }
        _ejercicios.add(ejercicio)
        return Resultado.Ok(ejercicio)
    }

    fun eliminarEjercicio(indice: Int): Resultado<Unit> {
        if (indice !in _ejercicios.indices) return Resultado.Error("El ejercicio no existe.")
        _ejercicios.removeAt(indice)
        return Resultado.Ok(Unit)
    }

    fun reiniciarProgreso() = _ejercicios.forEach { it.reiniciarProgreso() }
}