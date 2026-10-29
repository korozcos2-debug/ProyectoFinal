package com.gymapp.core

import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// ---------- utilidades de consola ----------

private fun leer(texto: String): String {
    print(texto)
    return (readlnOrNull() ?: exitProcess(0)).trim()
}

private fun informar(resultado: Resultado<*>) {
    resultado.mensajeONull()?.let { println("(!) $it") }
}

private fun entero(partes: List<String>, i: Int): Int? = partes.getOrNull(i)?.toIntOrNull()

private fun pedir(texto: String, accion: (String) -> Resultado<*>) {
    while (true) {
        val error = accion(leer(texto)).mensajeONull() ?: return
        println("(!) $error")
    }
}

// ---------- registro de usuario ----------

private fun registrar(repo: UsuarioRepositorio): Usuario {
    println("=== BIENVENIDO ===")
    val registro = RegistroUsuario()

    pedir("Nombre de usuario: ") { registro.establecerNombre(it) }

    var unidad = UnidadPeso.KG
    while (true) {
        when (leer("Unidad de peso (kg/lb): ").lowercase()) {
            "kg" -> { unidad = UnidadPeso.KG; break }
            "lb" -> { unidad = UnidadPeso.LB; break }
            else -> println("(!) Escribe kg o lb.")
        }
    }
    pedir("Peso (${unidad.etiqueta}): ") { registro.establecerPeso(it, unidad) }
    pedir("Altura en cm: ") { registro.establecerAltura(it) }

    println("Nivel de experiencia:")
    val niveles = NivelExperiencia.entries
    niveles.forEachIndexed { i, n -> println("  ${i + 1}) ${n.titulo} - ${n.descripcion}") }
    while (true) {
        val nivel = leer("Elige (1-${niveles.size}): ").toIntOrNull()?.let { niveles.getOrNull(it - 1) }
        if (nivel != null) {
            registro.establecerNivel(nivel)
            break
        }
        println("(!) Opción inválida.")
    }

    val usuario = registro.finalizar(repo).obtenerONull()!!
    println("\nPerfil creado: ${usuario.nombre} | ${fmt(usuario.peso)} ${usuario.unidadPeso.etiqueta} | " +
            "${fmt(usuario.alturaCm)} cm | ${usuario.nivel.titulo}")
    return usuario
}

// ---------- sesión de entrenamiento (con cronómetro) ----------

private fun mostrar(rutina: Rutina, crono: Cronometro) {
    println("\n=== ${rutina.nombre.uppercase()} (${(rutina.progreso * 100).toInt()}%) ===   Tiempo: ${crono.formato()}")
    if (rutina.ejercicios.isEmpty()) {
        println("(sin ejercicios)")
        return
    }
    rutina.ejercicios.forEachIndexed { i, ej ->
        println("${i + 1}. ${ej.nombre} (${ej.seriesCompletadas}/${ej.series.size})")
        ej.series.forEachIndexed { j, s ->
            val marca = if (s.completada) "[X]" else "[ ]"
            val peso = s.peso?.let { " x ${fmt(it)}" } ?: ""
            println("     ${j + 1}) ${s.repeticiones} reps$peso $marca")
        }
    }
}

private fun cerrarSesion(
    rutina: Rutina,
    crono: Cronometro,
    gestor: GestorRutinasPersonalizadas,
    informe: Informe,
    completa: Boolean
) {
    val segundos = crono.detener()
    println(if (completa) "\n*** ¡Rutina completada! ***" else "\nSesión detenida.")
    println("Tiempo total: ${formatoTiempo(segundos)}")

    val resultado = informe.guardar(rutina, segundos)
    val error = resultado.mensajeONull()
    if (error == null) println("Tu avance se guardó en Estadísticas.") else println("(!) $error")

    // Se reinicia la rutina para poder repetirla; lo guardado ya está en el informe.
    rutina.reiniciarProgreso()
    if (rutina.tipo == TipoRutina.PERSONALIZADA) gestor.guardar(rutina)
}

private fun sesion(rutina: Rutina, gestor: GestorRutinasPersonalizadas, informe: Informe) {
    val esPersonalizada = rutina.tipo == TipoRutina.PERSONALIZADA
    val crono = Cronometro()
    if (rutina.ejercicios.isNotEmpty()) {
        crono.iniciar()
        println("\nCronómetro iniciado.")
    }

    val ayuda = buildString {
        append("\nm <ej> <serie> marcar/desmarcar | r <ej> <serie> <reps> cambiar reps")
        append("\np <ej> <serie> <peso> peso de una serie | w <ej> <peso> peso a todas")
        append("\na <ej> [reps] agregar serie | z <ej> <serie> quitar serie")
        if (esPersonalizada) append("\nn nuevo ejercicio | b <ej> borrar ejercicio")
        append("\nx detener sesión")
    }

    while (true) {
        mostrar(rutina, crono)
        println(ayuda)
        val partes = leer("> ").split(" ").filter { it.isNotEmpty() }
        if (partes.isEmpty()) continue

        val cmd = partes[0].lowercase()
        val ej = entero(partes, 1)?.let { rutina.ejercicios.getOrNull(it - 1) }
        val serie = (entero(partes, 2) ?: 0) - 1

        when (cmd) {
            "m" -> if (ej == null) println("(!) Ejercicio inexistente.") else {
                val hecha = ej.series.getOrNull(serie)?.completada ?: false
                informar(ej.marcarSerie(serie, !hecha))
            }

            "r" -> {
                val reps = entero(partes, 3)
                if (ej == null) println("(!) Ejercicio inexistente.")
                else if (reps == null) println("(!) Uso: r <ej> <serie> <reps>")
                else informar(ej.cambiarRepeticiones(serie, reps))
            }

            "p" -> if (ej == null) println("(!) Ejercicio inexistente.") else {
                val peso = Validadores.pesoSerieTexto(partes.getOrNull(3) ?: "")
                if (peso.esOk) informar(ej.cambiarPeso(serie, peso.obtenerONull())) else informar(peso)
            }

            "w" -> if (ej == null) println("(!) Ejercicio inexistente.") else {
                val peso = Validadores.pesoSerieTexto(partes.getOrNull(2) ?: "")
                if (peso.esOk) informar(ej.aplicarPesoATodas(peso.obtenerONull())) else informar(peso)
            }

            "a" -> if (ej == null) println("(!) Ejercicio inexistente.") else {
                informar(ej.agregarSerie(entero(partes, 2) ?: Limites.REPS_POR_DEFECTO))
            }

            "z" -> if (ej == null) println("(!) Ejercicio inexistente.") else informar(ej.eliminarSerie(serie))

            "n" -> if (!esPersonalizada) println("(!) Solo disponible en rutinas personalizadas.") else {
                val nombre = leer("Nombre del ejercicio: ")
                val series = leer("Número de series: ").toIntOrNull() ?: 0
                val reps = leer("Repeticiones por serie: ").toIntOrNull() ?: 0
                informar(rutina.agregarEjercicio(nombre, series, reps))
                if (rutina.ejercicios.isNotEmpty()) crono.iniciar() // arranca con el primer ejercicio
            }

            "b" -> if (!esPersonalizada) println("(!) Solo disponible en rutinas personalizadas.") else {
                informar(rutina.eliminarEjercicio((entero(partes, 1) ?: 0) - 1))
            }

            "x" -> {
                val faltan = rutina.totalSeries - rutina.seriesCompletadas
                if (faltan > 0) {
                    println("\n(!) ATENCIÓN: aún te faltan $faltan series. Pendientes:")
                    rutina.ejercicios.filter { !it.completado }.forEach {
                        println("   - ${it.nombre} (${it.seriesCompletadas}/${it.series.size})")
                    }
                    val resp = leer("¿Seguro que quieres detener? (s/n): ").lowercase()
                    if (resp != "s" && resp != "si") continue
                }
                cerrarSesion(rutina, crono, gestor, informe, completa = false)
                return
            }

            else -> println("(!) Comando no reconocido.")
        }

        if (rutina.completada) {
            mostrar(rutina, crono)
            cerrarSesion(rutina, crono, gestor, informe, completa = true)
            return
        }
    }
}

// ---------- menús ----------

private fun menuEjercicios(gestor: GestorRutinasPersonalizadas, informe: Informe) {
    while (true) {
        println("\n=== EJERCICIOS ===")
        val grupos = GrupoMuscular.entries
        grupos.forEachIndexed { i, g -> println("${i + 1}) ${g.titulo}") }
        println("0) Volver")
        val opcion = leer("> ").toIntOrNull() ?: -1
        if (opcion == 0) return
        val grupo = grupos.getOrNull(opcion - 1)
        if (grupo == null) {
            println("(!) Opción inválida.")
            continue
        }
        sesion(Catalogo.crearRutina(grupo), gestor, informe)
    }
}

private fun menuPersonalizado(gestor: GestorRutinasPersonalizadas, informe: Informe) {
    while (true) {
        println("\n=== MODO PERSONALIZADO ===")
        val rutinas = gestor.listar()
        if (rutinas.isEmpty()) println("(no tienes rutinas creadas)")
        rutinas.forEachIndexed { i, r -> println("${i + 1}) ${r.nombre} (${r.ejercicios.size} ejercicios)") }
        println("n) Nuevo   d <num>) Eliminar   x) Volver   (o escribe el número para entrenar)")

        val partes = leer("> ").split(" ").filter { it.isNotEmpty() }
        if (partes.isEmpty()) continue

        when (val cmd = partes[0].lowercase()) {
            "x" -> return
            "n" -> {
                val resultado = gestor.crear(leer("Nombre de la rutina: "))
                val rutina = resultado.obtenerONull()
                if (rutina != null) sesion(rutina, gestor, informe) else informar(resultado)
            }
            "d" -> {
                val rutina = entero(partes, 1)?.let { rutinas.getOrNull(it - 1) }
                if (rutina == null) println("(!) Rutina inexistente.") else gestor.eliminar(rutina.id)
            }
            else -> {
                val rutina = cmd.toIntOrNull()?.let { rutinas.getOrNull(it - 1) }
                if (rutina == null) println("(!) Opción inválida.") else sesion(rutina, gestor, informe)
            }
        }
    }
}

// ---------- estadísticas / informe ----------

private fun mostrarHistorial(informe: Informe, unidad: UnidadPeso) {
    val sesiones = informe.sesiones()
    if (sesiones.isEmpty()) {
        println("(aún no hay sesiones guardadas)")
        return
    }
    sesiones.forEachIndexed { i, s ->
        println(
            "${i + 1}) ${s.fecha.format(FORMATO_FECHA)}  ${s.rutinaNombre} | " +
                    "series ${informe.seriesHechas(s)}/${informe.seriesTotales(s)} | " +
                    "volumen ${fmt(informe.volumen(s))} ${unidad.etiqueta} | " +
                    "tiempo ${formatoTiempo(informe.duracion(s))}"
        )
    }
}

private fun mostrarComparacion(informe: Informe, unidad: UnidadPeso) {
    val periodos = Periodo.entries
    periodos.forEachIndexed { i, p -> println("${i + 1}) ${p.titulo}") }
    val periodo = leer("Elige periodo: ").toIntOrNull()?.let { periodos.getOrNull(it - 1) }
    if (periodo == null) {
        println("(!) Opción inválida.")
        return
    }
    val r = informe.comparar(periodo, unidad)
    println("\n=== ${periodo.titulo.uppercase()} ===")
    println(r.mensaje)
    println("Sesiones: ${periodo.etiquetaActual} ${r.sesionesActual} | ${periodo.etiquetaAnterior} ${r.sesionesAnterior}")
    println("Volumen: ${fmt(r.volumenActual)} vs ${fmt(r.volumenAnterior)} ${unidad.etiqueta}")
    if (r.comparaciones.isNotEmpty()) {
        println("Por ejercicio:")
        r.comparaciones.forEach { println("  - ${it.nombre}: ${it.mensaje}") }
    }
}

private fun menuEstadisticas(informe: Informe, usuario: Usuario) {
    val unidad = usuario.unidadPeso
    while (true) {
        println("\n=== ESTADÍSTICAS / INFORME ===")
        println(
            "Sesiones: ${informe.sesiones().size} | Tiempo total: ${formatoTiempo(informe.tiempoTotal())} | " +
                    "Series: ${informe.seriesTotalesHechas()} | Volumen: ${fmt(informe.volumenTotal())} ${unidad.etiqueta}"
        )
        println("1) Historial de sesiones\n2) Comparar progreso\nd <num>) Eliminar registro\n0) Volver")

        val partes = leer("> ").split(" ").filter { it.isNotEmpty() }
        if (partes.isEmpty()) continue

        when (partes[0].lowercase()) {
            "0" -> return
            "1" -> mostrarHistorial(informe, unidad)
            "2" -> mostrarComparacion(informe, unidad)
            "d" -> {
                val sesion = entero(partes, 1)?.let { informe.sesiones().getOrNull(it - 1) }
                if (sesion == null) println("(!) Registro inexistente.") else {
                    informe.eliminar(sesion)
                    println("Registro eliminado.")
                }
            }
            else -> println("(!) Opción inválida.")
        }
    }
}

// ---------- programa principal ----------

fun main() {
    val usuarios = UsuarioRepositorioMemoria()
    val gestor = GestorRutinasPersonalizadas(RutinaRepositorioMemoria())
    val informe = Informe(Historial(HistorialRepositorioMemoria()))

    val usuario = registrar(usuarios)

    while (true) {
        println("\nHola, ${usuario.nombre}")
        println("1) Ejercicios\n2) Modo personalizado\n3) Estadísticas / Informe\n0) Salir")
        when (leer("> ")) {
            "1" -> menuEjercicios(gestor, informe)
            "2" -> menuPersonalizado(gestor, informe)
            "3" -> menuEstadisticas(informe, usuario)
            "0" -> {
                println("¡Hasta luego!")
                return
            }
            else -> println("(!) Opción inválida.")
        }
    }
}