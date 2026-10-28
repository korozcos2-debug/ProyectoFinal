package com.gymapp.core

object Validadores {

    // Empieza con letra; luego letras, números o guion bajo. Total: 3 a 20 caracteres. Ej: "kenedy"
    private val REGEX_NOMBRE_USUARIO = Regex("^\\p{L}[\\p{L}0-9_]{2,19}$")

    // Solo dígitos, con decimales opcionales (punto o coma, máx. 2). Rechaza letras, signos y negativos.
    private val REGEX_NUMERO = Regex("^\\d+([.,]\\d{1,2})?$")

    const val ALTURA_MIN_CM = 50.0
    const val ALTURA_MAX_CM = 272.0

    fun nombreUsuario(texto: String): Resultado<String> {
        val t = texto.trim()
        if (t.isEmpty()) return Resultado.Error("El nombre de usuario es obligatorio.")
        if (!REGEX_NOMBRE_USUARIO.matches(t)) {
            return Resultado.Error(
                "Nombre inválido: usa de 3 a 20 caracteres (letras, números o _), empezando con una letra."
            )
        }
        return Resultado.Ok(t)
    }

    fun peso(texto: String, unidad: UnidadPeso): Resultado<Double> {
        val valor = numero(texto, "El peso") 
        if (valor is Resultado.Error) return valor
        val v = (valor as Resultado.Ok).valor
        val (min, max) = when (unidad) {
            UnidadPeso.KG -> 20.0 to 500.0
            UnidadPeso.LB -> 44.0 to 1100.0
        }
        if (v < min || v > max) {
            return Resultado.Error("El peso debe estar entre ${min.toInt()} y ${max.toInt()} ${unidad.etiqueta}.")
        }
        return Resultado.Ok(v)
    }

    fun altura(texto: String): Resultado<Double> {
        val valor = numero(texto, "La altura")
        if (valor is Resultado.Error) return valor
        val v = (valor as Resultado.Ok).valor
        if (v < ALTURA_MIN_CM || v > ALTURA_MAX_CM) {
            return Resultado.Error("La altura debe estar entre ${ALTURA_MIN_CM.toInt()} y ${ALTURA_MAX_CM.toInt()} cm.")
        }
        return Resultado.Ok(v)
    }

    fun repeticiones(valor: Int): Resultado<Int> =
        if (valor in Limites.REPS_MIN..Limites.REPS_MAX) Resultado.Ok(valor)
        else Resultado.Error("Las repeticiones deben estar entre ${Limites.REPS_MIN} y ${Limites.REPS_MAX}.")

    /** Nombre libre de rutina o ejercicio (no vacío, longitud máxima). */
    /** Peso de una serie ya convertido a número. null significa "sin peso registrado". */
    fun pesoSerie(valor: Double?): Resultado<Double?> {
        if (valor == null) return Resultado.Ok(null)
        if (valor.isNaN() || valor <= 0.0 || valor > Limites.PESO_SERIE_MAX) {
            return Resultado.Error("El peso debe ser mayor que 0 y máximo ${Limites.PESO_SERIE_MAX.toInt()}.")
        }
        return Resultado.Ok(valor)
    }

    /** Igual que pesoSerie, pero recibe el texto escrito por el usuario. Vacío = sin peso. */
    fun pesoSerieTexto(texto: String): Resultado<Double?> {
        val t = texto.trim()
        if (t.isEmpty()) return Resultado.Ok(null)
        if (!REGEX_NUMERO.matches(t)) {
            return Resultado.Error("El peso solo acepta números positivos (sin letras ni signos).")
        }
        return pesoSerie(t.replace(',', '.').toDouble())
    }
    fun textoNombre(texto: String, campo: String): Resultado<String> {
        val t = texto.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return Resultado.Error("$campo no puede estar vacío.")
        if (t.length > Limites.NOMBRE_TEXTO_MAX) {
            return Resultado.Error("$campo no puede superar ${Limites.NOMBRE_TEXTO_MAX} caracteres.")
        }
        return Resultado.Ok(t)
    }

    private fun numero(texto: String, campo: String): Resultado<Double> {
        val t = texto.trim()
        if (t.isEmpty()) return Resultado.Error("$campo es obligatorio.")
        if (!REGEX_NUMERO.matches(t)) return Resultado.Error("$campo solo acepta números positivos (sin letras ni signos).")
        val v = t.replace(',', '.').toDouble()
        if (v <= 0.0) return Resultado.Error("$campo debe ser mayor que 0.")
        return Resultado.Ok(v)
    }
}
