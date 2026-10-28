package com.gymapp.core

/** Resultado de una operación: éxito con valor, o error con mensaje listo para mostrar en la UI. */
sealed class Resultado<out T> {
    data class Ok<out T>(val valor: T) : Resultado<T>()
    data class Error(val mensaje: String) : Resultado<Nothing>()

    val esOk: Boolean get() = this is Ok

    fun obtenerONull(): T? = (this as? Ok)?.valor

    fun mensajeONull(): String? = (this as? Error)?.mensaje
}
