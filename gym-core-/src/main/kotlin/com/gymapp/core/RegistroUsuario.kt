package com.gymapp.core

/**
 * Flujo de inicio: nombre -> peso -> altura -> nivel -> finalizar.
 * Cada paso valida y NO guarda nada si el dato es inválido.
 */
class RegistroUsuario {
    var nombre: String? = null
        private set
    var peso: Double? = null
        private set
    var unidadPeso: UnidadPeso? = null
        private set
    var alturaCm: Double? = null
        private set
    var nivel: NivelExperiencia? = null
        private set

    fun establecerNombre(texto: String): Resultado<String> {
        val r = Validadores.nombreUsuario(texto)
        if (r is Resultado.Ok) nombre = r.valor
        return r
    }

    fun establecerPeso(texto: String, unidad: UnidadPeso): Resultado<Double> {
        val r = Validadores.peso(texto, unidad)
        if (r is Resultado.Ok) { peso = r.valor; unidadPeso = unidad }
        return r
    }

    fun establecerAltura(texto: String): Resultado<Double> {
        val r = Validadores.altura(texto)
        if (r is Resultado.Ok) alturaCm = r.valor
        return r
    }

    fun establecerNivel(nivel: NivelExperiencia) { this.nivel = nivel }

    val puedeFinalizar: Boolean
        get() = nombre != null && peso != null && alturaCm != null && nivel != null

    fun finalizar(repositorio: UsuarioRepositorio): Resultado<Usuario> {
        val n = nombre ?: return Resultado.Error("Falta el nombre de usuario.")
        val p = peso ?: return Resultado.Error("Falta el peso.")
        val u = unidadPeso ?: return Resultado.Error("Falta la unidad de peso.")
        val a = alturaCm ?: return Resultado.Error("Falta la altura.")
        val lvl = nivel ?: return Resultado.Error("Selecciona tu nivel de experiencia.")
        val usuario = Usuario(n, p, u, a, lvl)
        repositorio.guardar(usuario)
        return Resultado.Ok(usuario)
    }
}
