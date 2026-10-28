package com.gymapp.core

// Demo de consola para probar la lógica. Esta parte NO va a Android: la UI la hará Android Studio.

private fun leer(texto: String): String {
    print(texto)
    return readlnOrNull()?.trim() ?: ""
}

private fun Resultado<*>.informar() {
    if (this is Resultado.Error) println("  ✗ $mensaje")
}

private fun List<String>.entero(i: Int): Int? = getOrNull(i)?.toIntOrNull()

private fun pedir(texto: String, accion: (String) -> Resultado<*>) {
    while (true) {
        val r = accion(leer(texto))
        if (r is Resultado.Error) println("  ✗ ${r.mensaje}") else return
    }
}

private fun registrar(repo: UsuarioRepositorio) {
    println("=== BIENVENIDO ===")
    val reg = RegistroUsuario()

    pedir("Nombre de usuario: ") { reg.establecerNombre(it) }

    var unidad: UnidadPeso? = null
    while (unidad == null) {
        unidad = when (leer("Unidad de peso (kg/lb): ").lowercase()) {
            "kg" -> UnidadPeso.KG
            "lb" -> UnidadPeso.LB
            else -> { println("  ✗ Escribe kg o lb."); null }
        }
    }
    pedir("Peso en ${unidad.etiqueta}: ") { reg.establecerPeso(it, unidad) }
    pedir("Altura en cm: ") { reg.establecerAltura(it) }

    println("Nivel de experiencia:")
    NivelExperiencia.entries.forEachIndexed { i, n -> println("  ${i + 1}) ${n.titulo} - ${n.descripcion}") }
    var nivel: NivelExperiencia? = null
    while (nivel == null) {
        nivel = NivelExperiencia.entries.getOrNull((leer("Elige (1-3): ").toIntOrNull() ?: 0) - 1)
        if (nivel == null) println("  ✗ Opción inválida.")
    }
    reg.establecerNivel(nivel)

    val usuario = (reg.finalizar(repo) as Resultado.Ok).valor
    println("\n✓ Perfil creado: ${usuario.nombre}, ${usuario.peso} ${usuario.unidadPeso.etiqueta}, " +
            "${usuario.alturaCm} cm, ${usuario.nivel.titulo}\n")
}

private fun mostrar(rutina: Rutina) {
    println("\n--- ${rutina.nombre.uppercase()}  (${(rutina.progreso * 100).toInt()}% completado) ---")
    if (rutina.ejercicios.isEmpty()) println("  (sin ejercicios)")
    rutina.ejercicios.forEachIndexed { i, e ->
        println("${i + 1}. ${e.nombre}  [${e.seriesCompletadas}/${e.series.size}]")
        e.series.forEachIndexed { j, s ->
            println("     serie ${j + 1}: ${s.repeticiones} reps  ${if (s.completada) "[X]" else "[ ]"}")
        }
    }
    if (rutina.completada) println("\n*** ¡Rutina completada! ***")
}

private fun sesion(rutina: Rutina, gestor: GestorRutinasPersonalizadas?) {
    while (true) {
        mostrar(rutina)
        println("\nm <ej> <serie> marcar/desmarcar | r <ej> <serie> <reps> cambiar reps")
        println("a <ej> agregar serie | q <ej> <serie> quitar serie | z reiniciar" +
                (if (gestor != null) " | n nuevo ejercicio | b <ej> borrar ejercicio" else "") + " | x volver")
        val t = leer("> ").split(" ").filter { it.isNotEmpty() }
        if (t.isEmpty()) continue
        val ej = rutina.ejercicios.getOrNull((t.entero(1) ?: 0) - 1)
        val iSerie = (t.entero(2) ?: 0) - 1

        when (t[0].lowercase()) {
            "x" -> { gestor?.guardar(rutina); return }
            "z" -> rutina.reiniciarProgreso()
            "m" -> if (ej == null) println("  ✗ Ejercicio inexistente.") else {
                val actual = ej.series.getOrNull(iSerie)?.completada ?: false
                ej.marcarSerie(iSerie, !actual).informar()
            }
            "r" -> if (ej == null) println("  ✗ Ejercicio inexistente.")
                   else ej.cambiarRepeticiones(iSerie, t.entero(3) ?: 0).informar()
            "a" -> if (ej == null) println("  ✗ Ejercicio inexistente.") else ej.agregarSerie().informar()
            "q" -> if (ej == null) println("  ✗ Ejercicio inexistente.") else ej.eliminarSerie(iSerie).informar()
            "n" -> if (gestor != null) {
                val nombre = leer("Nombre del ejercicio: ")
                val series = leer("Número de series: ").toIntOrNull() ?: 0
                val reps = leer("Repeticiones por serie: ").toIntOrNull() ?: 0
                rutina.agregarEjercicio(nombre, series, reps).informar()
            }
            "b" -> if (gestor != null) rutina.eliminarEjercicio((t.entero(1) ?: 0) - 1).informar()
            else -> println("  ✗ Comando no reconocido.")
        }
    }
}

private fun menuEjercicios() {
    while (true) {
        println("\n=== EJERCICIOS ===")
        GrupoMuscular.entries.forEachIndexed { i, g -> println("  ${i + 1}) ${g.titulo}") }
        println("  0) Volver")
        val op = leer("> ").toIntOrNull() ?: -1
        if (op == 0) return
        val grupo = GrupoMuscular.entries.getOrNull(op - 1)
        if (grupo == null) println("  ✗ Opción inválida.") else sesion(Catalogo.crearRutina(grupo), null)
    }
}

private fun menuPersonalizado(gestor: GestorRutinasPersonalizadas) {
    while (true) {
        println("\n=== MODO PERSONALIZADO ===")
        val rutinas = gestor.listar()
        if (rutinas.isEmpty()) println("  (no tienes rutinas creadas)")
        rutinas.forEachIndexed { i, r -> println("  ${i + 1}) ${r.nombre}  [${r.ejercicios.size} ejercicios]") }
        println("  n) Nuevo   d <num>) Eliminar   x) Volver")
        val t = leer("> ").split(" ").filter { it.isNotEmpty() }
        if (t.isEmpty()) continue
        when {
            t[0].lowercase() == "x" -> return
            t[0].lowercase() == "n" -> {
                when (val r = gestor.crear(leer("Nombre de la rutina: "))) {
                    is Resultado.Ok -> sesion(r.valor, gestor)
                    is Resultado.Error -> println("  ✗ ${r.mensaje}")
                }
            }
            t[0].lowercase() == "d" -> {
                val r = rutinas.getOrNull((t.entero(1) ?: 0) - 1)
                if (r == null) println("  ✗ Rutina inexistente.") else gestor.eliminar(r.id)
            }
            else -> {
                val r = rutinas.getOrNull((t[0].toIntOrNull() ?: 0) - 1)
                if (r == null) println("  ✗ Opción inválida.") else sesion(r, gestor)
            }
        }
    }
}

fun main() {
    val usuarioRepo = UsuarioRepositorioMemoria()
    val rutinaRepo = RutinaRepositorioMemoria()
    val gestor = GestorRutinasPersonalizadas(rutinaRepo)

    registrar(usuarioRepo)

    while (true) {
        println("=== MENÚ PRINCIPAL (${usuarioRepo.obtener()?.nombre}) ===")
        println("  1) Ejercicios\n  2) Modo personalizado\n  0) Salir")
        when (leer("> ")) {
            "1" -> menuEjercicios()
            "2" -> menuPersonalizado(gestor)
            "0" -> { println("¡Hasta luego!"); return }
            else -> println("  ✗ Opción inválida.")
        }
    }
}
