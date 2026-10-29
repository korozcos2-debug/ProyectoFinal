package com.gymapp.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.system.exitProcess

private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ES: Locale = Locale.forLanguageTag("es")

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

private fun nombreDia(f: LocalDate): String =
    f.dayOfWeek.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() }

/** Pide el peso de una serie en la unidad del usuario. ENTER = sin peso. */
private fun pedirPeso(unidad: UnidadPeso): Double? {
    while (true) {
        val texto = leer("Peso por serie en ${unidad.etiqueta} (ENTER para omitir): ")
        if (texto.isEmpty()) return null
        val r = Validadores.pesoSerieTexto(texto)
        if (r.esOk) return r.obtenerONull()
        informar(r)
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

private fun mostrar(rutina: Rutina, crono: Cronometro, unidad: UnidadPeso) {
    println("\n=== ${rutina.nombre.uppercase()} (${(rutina.progreso * 100).toInt()}%) ===   Tiempo: ${crono.formato()}")
    if (rutina.ejercicios.isEmpty()) {
        println("(sin ejercicios)")
        return
    }
    rutina.ejercicios.forEachIndexed { i, ej ->
        println("${i + 1}. ${ej.nombre} (${ej.seriesCompletadas}/${ej.series.size})")
        ej.series.forEachIndexed { j, s ->
            val marca = if (s.completada) "[X]" else "[ ]"
            val peso = s.peso?.let { " x ${fmt(it)} ${unidad.etiqueta}" } ?: ""
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

    val error = informe.guardar(rutina, segundos).mensajeONull()
    if (error == null) println("Tu avance se guardó en Estadísticas.") else println("(!) $error")

    // Se reinicia la rutina para poder repetirla; lo guardado ya está en el informe.
    rutina.reiniciarProgreso()
    if (rutina.tipo == TipoRutina.PERSONALIZADA) gestor.guardar(rutina)
}

private fun sesion(
    rutina: Rutina,
    gestor: GestorRutinasPersonalizadas,
    informe: Informe,
    unidad: UnidadPeso
) {
    val esPersonalizada = rutina.tipo == TipoRutina.PERSONALIZADA
    val crono = Cronometro()
    if (rutina.ejercicios.isNotEmpty()) {
        crono.iniciar()
        println("\nCronómetro iniciado.")
    }

    val ayuda = buildString {
        append("\nm <ej> <serie> marcar/desmarcar | r <ej> <serie> <reps> cambiar reps")
        append("\np <ej> <serie> <peso> peso de una serie (${unidad.etiqueta}) | w <ej> <peso> peso a todas")
        append("\na <ej> [reps] [peso] agregar serie | z <ej> <serie> quitar serie")
        if (esPersonalizada) append("\nn nuevo ejercicio | b <ej> borrar ejercicio")
        append("\nx detener sesión")
    }

    while (true) {
        mostrar(rutina, crono, unidad)
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
                val reps = entero(partes, 2) ?: Limites.REPS_POR_DEFECTO
                val textoPeso = partes.getOrNull(3)
                if (textoPeso == null) {
                    informar(ej.agregarSerie(reps))
                } else {
                    val peso = Validadores.pesoSerieTexto(textoPeso)
                    if (peso.esOk) informar(ej.agregarSerie(reps, peso.obtenerONull())) else informar(peso)
                }
            }

            "z" -> if (ej == null) println("(!) Ejercicio inexistente.") else informar(ej.eliminarSerie(serie))

            "n" -> if (!esPersonalizada) println("(!) Solo disponible en rutinas personalizadas.") else {
                val nombre = leer("Nombre del ejercicio: ")
                val series = leer("Número de series: ").toIntOrNull() ?: 0
                val reps = leer("Repeticiones por serie: ").toIntOrNull() ?: 0
                val peso = pedirPeso(unidad)
                informar(rutina.agregarEjercicio(nombre, series, reps, peso))
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
            mostrar(rutina, crono, unidad)
            cerrarSesion(rutina, crono, gestor, informe, completa = true)
            return
        }
    }
}

// ---------- menús de entrenamiento ----------

private fun menuEjercicios(gestor: GestorRutinasPersonalizadas, informe: Informe, unidad: UnidadPeso) {
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
        sesion(Catalogo.crearRutina(grupo), gestor, informe, unidad)
    }
}

private fun menuPersonalizado(gestor: GestorRutinasPersonalizadas, informe: Informe, unidad: UnidadPeso) {
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
                if (rutina != null) sesion(rutina, gestor, informe, unidad) else informar(resultado)
            }
            "d" -> {
                val rutina = entero(partes, 1)?.let { rutinas.getOrNull(it - 1) }
                if (rutina == null) println("(!) Rutina inexistente.") else gestor.eliminar(rutina.id)
            }
            else -> {
                val rutina = cmd.toIntOrNull()?.let { rutinas.getOrNull(it - 1) }
                if (rutina == null) println("(!) Opción inválida.") else sesion(rutina, gestor, informe, unidad)
            }
        }
    }
}

// ---------- estadísticas / informe ----------

private fun mostrarHistorial(informe: Informe, unidad: UnidadPeso) {
    val dias = informe.sesionesPorDia()
    if (dias.isEmpty()) {
        println("(aún no hay sesiones guardadas)")
        return
    }
    var n = 0
    for ((fecha, lista) in dias) {
        println(
            "\n${nombreDia(fecha)} ${fecha.format(FORMATO_FECHA)} - ${lista.size} sesión(es) | " +
                    "tiempo ${formatoTiempo(lista.sumOf { informe.duracion(it) })} | " +
                    "volumen ${fmt(lista.sumOf { informe.volumen(it) })} ${unidad.etiqueta}"
        )
        for (s in lista) {
            n++
            val hora = informe.hora(s)?.format(FORMATO_HORA) ?: "--:--"
            println(
                "  $n) $hora  ${s.rutinaNombre} | series ${informe.seriesHechas(s)}/${informe.seriesTotales(s)} | " +
                        "volumen ${fmt(informe.volumen(s))} ${unidad.etiqueta} | tiempo ${formatoTiempo(informe.duracion(s))}"
            )
        }
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

private fun menuEstadisticas(informe: Informe, unidad: UnidadPeso) {
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
                val sesion = entero(partes, 1)?.let { informe.sesionesPlanas().getOrNull(it - 1) }
                if (sesion == null) println("(!) Registro inexistente.") else {
                    informe.eliminar(sesion)
                    println("Registro eliminado.")
                }
            }
            else -> println("(!) Opción inválida.")
        }
    }
}

// ---------- peso corporal ----------

private fun menuPeso(usuario: Usuario, repo: UsuarioRepositorio, pesos: HistorialPeso): Usuario {
    var actual = usuario
    val unidad = actual.unidadPeso.etiqueta
    while (true) {
        val lista = pesos.listar()
        println("\n=== MI PESO ===")
        println("Peso actual: ${fmt(actual.peso)} $unidad")

        val tendencia = when (pesos.tendencia()) {
            Tendencia.SUBIO -> "subiendo"
            Tendencia.BAJO -> "bajando"
            Tendencia.IGUAL -> "estable"
            else -> "aún sin datos suficientes"
        }
        println("Tendencia: $tendencia")
        pesos.cambioTotal()?.let {
            val signo = if (it > 0) "+" else ""
            println("Cambio desde el primer registro: $signo${fmt(it)} $unidad")
        }

        println("\nHistorial de cambios:")
        lista.forEachIndexed { i, r ->
            val dif = if (i == 0) "(inicial)" else {
                val d = r.peso - lista[i - 1].peso
                (if (d > 0) "+" else "") + fmt(d)
            }
            println("  ${i + 1}) ${r.fecha.format(FORMATO_FECHA)} ${r.hora.format(FORMATO_HORA)}  ${fmt(r.peso)} $unidad  $dif")
        }

        println("\n1) Actualizar mi peso\n0) Volver")
        when (leer("> ")) {
            "0" -> return actual
            "1" -> {
                val texto = leer("Nuevo peso en $unidad (ENTER para cancelar): ")
                if (texto.isEmpty()) continue
                val valor = Validadores.peso(texto, actual.unidadPeso)
                val numero = valor.obtenerONull()
                if (numero == null) {
                    informar(valor)
                } else {
                    actual = actual.copy(peso = numero)
                    repo.guardar(actual)
                    pesos.registrar(numero)
                    println("Peso actualizado.")
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
    val informe = Informe(HistorialRepositorioMemoria())
    val pesos = HistorialPeso()

    var usuario = registrar(usuarios)
    pesos.registrar(usuario.peso) // primer registro de peso

    while (true) {
        println("\nHola, ${usuario.nombre} (${fmt(usuario.peso)} ${usuario.unidadPeso.etiqueta})")
        println("1) Ejercicios\n2) Modo personalizado\n3) Estadísticas / Informe\n4) Mi peso\n0) Salir")
        when (leer("> ")) {
            "1" -> menuEjercicios(gestor, informe, usuario.unidadPeso)
            "2" -> menuPersonalizado(gestor, informe, usuario.unidadPeso)
            "3" -> menuEstadisticas(informe, usuario.unidadPeso)
            "4" -> usuario = menuPeso(usuario, usuarios, pesos)
            "0" -> {
                println("¡Hasta luego!")
                return
            }
            else -> println("(!) Opción inválida.")
        }
    }
}