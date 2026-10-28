package com.gymapp.core

/** Fila de las tablas de rutinas guía: zona muscular y series orientativas. */
data class ZonaPlantilla(
    val orden: Int,
    val zona: String,
    val seriesMin: Int,
    val seriesMax: Int
) {
    val textoSeries: String get() = if (seriesMin == seriesMax) "$seriesMin" else "$seriesMin–$seriesMax"
}

object Catalogo {

    val PECHO = listOf(
        ZonaPlantilla(1, "Pecho superior", 3, 3),
        ZonaPlantilla(2, "Pecho medio", 3, 3),
        ZonaPlantilla(3, "Pecho inferior", 2, 3),
        ZonaPlantilla(4, "Deltoide lateral", 3, 3),
        ZonaPlantilla(5, "Deltoide posterior", 2, 3),
        ZonaPlantilla(6, "Tríceps: cabeza larga", 2, 3),
        ZonaPlantilla(7, "Tríceps: cabezas lateral y medial", 2, 3)
    )

    val ESPALDA = listOf(
        ZonaPlantilla(1, "Dorsal ancho (amplitud)", 3, 3),
        ZonaPlantilla(2, "Espalda media: romboides y trapecio medio", 3, 3),
        ZonaPlantilla(3, "Trapecio superior e inferior", 2, 3),
        ZonaPlantilla(4, "Zona lumbar: erectores espinales", 2, 2),
        ZonaPlantilla(5, "Bíceps: cabeza larga y corta", 3, 4),
        ZonaPlantilla(6, "Braquial", 2, 2),
        ZonaPlantilla(7, "Braquiorradial", 2, 2),
        ZonaPlantilla(8, "Flexores del antebrazo", 2, 2),
        ZonaPlantilla(9, "Extensores del antebrazo", 2, 2)
    )

    val TREN_INFERIOR = listOf(
        ZonaPlantilla(1, "Cuádriceps", 3, 4),
        ZonaPlantilla(2, "Femorales", 3, 4),
        ZonaPlantilla(3, "Glúteos", 3, 4),
        ZonaPlantilla(4, "Aductores", 2, 3),
        ZonaPlantilla(5, "Pantorrillas", 3, 4),
        ZonaPlantilla(6, "Tibial anterior", 2, 2),
        ZonaPlantilla(7, "Abdomen", 3, 3)
    )

    fun plantilla(grupo: GrupoMuscular): List<ZonaPlantilla> = when (grupo) {
        GrupoMuscular.PECHO -> PECHO
        GrupoMuscular.ESPALDA -> ESPALDA
        GrupoMuscular.TREN_INFERIOR -> TREN_INFERIOR
    }

    /**
     * Crea una rutina NUEVA (copia editable) a partir de la plantilla.
     * Empieza con el máximo de series orientativas; el usuario puede quitar o agregar.
     */
    fun crearRutina(grupo: GrupoMuscular): Rutina {
        val rutina = Rutina(
            id = "predefinida_${grupo.name.lowercase()}",
            nombre = grupo.titulo,
            tipo = TipoRutina.PREDEFINIDA,
            grupo = grupo
        )
        plantilla(grupo).forEach { rutina.agregarEjercicio(it.zona, it.seriesMax, Limites.REPS_POR_DEFECTO) }
        return rutina
    }
}
