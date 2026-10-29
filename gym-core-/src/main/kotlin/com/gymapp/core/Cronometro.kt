package com.gymapp.core

class Cronometro {
    private var inicioNs = 0L
    private var acumuladoNs = 0L
    var corriendo = false
        private set

    fun iniciar() {
        if (!corriendo) {
            inicioNs = System.nanoTime()
            corriendo = true
        }
    }

    /** Detiene el cronómetro y devuelve los segundos totales. */
    fun detener(): Long {
        if (corriendo) {
            acumuladoNs += System.nanoTime() - inicioNs
            corriendo = false
        }
        return segundos
    }

    val segundos: Long
        get() {
            val extra = if (corriendo) System.nanoTime() - inicioNs else 0L
            return (acumuladoNs + extra) / 1_000_000_000L
        }

    fun formato(): String = formatoTiempo(segundos)
}

fun formatoTiempo(totalSegundos: Long): String {
    val h = totalSegundos / 3600
    val m = (totalSegundos % 3600) / 60
    val s = totalSegundos % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}