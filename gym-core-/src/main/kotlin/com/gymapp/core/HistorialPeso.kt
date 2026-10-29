package com.gymapp.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

data class RegistroPeso(val fecha: LocalDate, val hora: LocalTime, val peso: Double)

/** Historial del peso corporal. Cada cambio queda guardado con fecha y hora del sistema. */
class HistorialPeso(private val reloj: Clock = Clock.systemDefaultZone()) {

    private val registros = mutableListOf<RegistroPeso>()

    fun registrar(peso: Double): RegistroPeso {
        val r = RegistroPeso(LocalDate.now(reloj), LocalTime.now(reloj).withNano(0), peso)
        registros.add(r)
        return r
    }

    fun listar(): List<RegistroPeso> = registros.toList()

    fun cambioTotal(): Double? =
        if (registros.size < 2) null else registros.last().peso - registros.first().peso

    /** Compara el último registro con el anterior. */
    fun tendencia(): Tendencia {
        if (registros.size < 2) return Tendencia.SIN_DATOS
        val diff = registros.last().peso - registros[registros.size - 2].peso
        return when {
            diff > 1e-9 -> Tendencia.SUBIO
            diff < -1e-9 -> Tendencia.BAJO
            else -> Tendencia.IGUAL
        }
    }
}