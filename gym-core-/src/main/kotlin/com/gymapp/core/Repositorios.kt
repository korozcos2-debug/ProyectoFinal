package com.gymapp.core

/**
 * Contratos de almacenamiento local.
 * En Android los implementaremos (Room, DataStore o archivo JSON).
 * Aquí van versiones en memoria para probar la lógica.
 */
interface UsuarioRepositorio {
    fun obtener(): Usuario?
    fun guardar(usuario: Usuario)
}

interface RutinaRepositorio {
    fun listar(): List<Rutina>
    fun obtener(id: String): Rutina?
    fun guardar(rutina: Rutina)   // crea o actualiza
    fun eliminar(id: String)
}

/** Almacenamiento del historial de entrenamientos (los avances). */
interface HistorialRepositorio {
    fun listar(): List<RegistroSesion>
    fun guardar(registro: RegistroSesion)   // crea o actualiza por id
    fun eliminar(id: String)
}

class UsuarioRepositorioMemoria : UsuarioRepositorio {
    private var usuario: Usuario? = null
    override fun obtener() = usuario
    override fun guardar(usuario: Usuario) { this.usuario = usuario }
}

class RutinaRepositorioMemoria : RutinaRepositorio {
    private val datos = linkedMapOf<String, Rutina>()
    override fun listar() = datos.values.toList()
    override fun obtener(id: String) = datos[id]
    override fun guardar(rutina: Rutina) { datos[rutina.id] = rutina }
    override fun eliminar(id: String) { datos.remove(id) }
}

class HistorialRepositorioMemoria : HistorialRepositorio {
    private val datos = linkedMapOf<String, RegistroSesion>()
    override fun listar() = datos.values.toList()
    override fun guardar(registro: RegistroSesion) { datos[registro.id] = registro }
    override fun eliminar(id: String) { datos.remove(id) }
}