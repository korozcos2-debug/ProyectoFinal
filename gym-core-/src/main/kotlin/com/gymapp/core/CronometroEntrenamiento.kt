
package com.gymapp.core

/**
 * Cronómetro de una sesión de gimnasio.
 *
 * Se inicia al comenzar la rutina y se detiene al finalizarla.
 * Utiliza nanoTime para medir el tiempo transcurrido.
 */
class CronometroEntrenamiento {

    private var inicio: Long? = null
    private var final: Long? = null

    val estaActivo: Boolean
        get() = inicio != null && final == null

    fun iniciar() {
        inicio = System.nanoTime()
        final = null
    }

    fun detener(): Long {
        val inicioActual = inicio
            ?: return 0L

        if (final == null) {
            final = System.nanoTime()
        }

        return (final!! - inicioActual) / 1_000_000_000L
    }

    fun segundosTranscurridos(): Long {
        val inicioActual = inicio ?: return 0L
        val finActual = final ?: System.nanoTime()

        return (finActual - inicioActual) / 1_000_000_000L
    }

    fun obtenerTiempoFormateado(): String {
        val segundos = segundosTranscurridos()

        val horas = segundos / 3600
        val minutos = (segundos % 3600) / 60
        val restantes = segundos % 60

        return "%02d:%02d:%02d".format(
            horas, minutos, restantes
        )
    }

    fun reiniciar() {
        inicio = null
        final = null
    }
}
