package com.example.icm_papus_taller3

data class User(
    val uid: String = "",
    val nombre: String = "",
    val apellido: String = "",
    val email: String = "",
    val numeroIdentificacion: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val fotoUrl: String = ""
)
