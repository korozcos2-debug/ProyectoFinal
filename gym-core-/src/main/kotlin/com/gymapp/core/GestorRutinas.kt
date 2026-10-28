package com.gymapp.core

import java.util.UUID

/** Modo "Personalizado": el usuario crea, edita y elimina sus propias rutinas. */
class GestorRutinasPersonalizadas(private val repositorio: RutinaRepositorio) {

    fun listar(): List<Rutina> = repositorio.listar().filter { it.tipo == TipoRutina.PERSONALIZADA }

    /** Botón "Nuevo": crea una rutina vacía con el nombre elegido y la guarda. */
    fun crear(nombre: String): Resultado<Rutina> {
        val nombreOk = when (val r = Validadores.textoNombre(nombre, "El nombre de la rutina")) {
            is Resultado.Ok -> r.valor
            is Resultado.Error -> return r
        }
        if (listar().any { it.nombre.equals(nombreOk, ignoreCase = true) }) {
            return Resultado.Error("Ya existe una rutina llamada \"$nombreOk\".")
        }
        val rutina = Rutina(UUID.randomUUID().toString(), nombreOk, TipoRutina.PERSONALIZADA)
        repositorio.guardar(rutina)
        return Resultado.Ok(rutina)
    }

    /** Botón "Guardar": persiste los cambios hechos a la rutina (ejercicios, series, reps). */
    fun guardar(rutina: Rutina) = repositorio.guardar(rutina)

    fun eliminar(id: String) = repositorio.eliminar(id)
}
