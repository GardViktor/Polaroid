package com.polaroid.app.navegacao

import androidx.annotation.DrawableRes
import com.polaroid.app.R

/** Abas do menu inferior, na ordem em que aparecem. */
enum class Destino(val rotulo: String, @param:DrawableRes val icone: Int) {
    CALCULADORA("Calcular", R.drawable.ic_calcular),
    BUSCAR("Buscar", R.drawable.ic_buscar),
    ALBUM("Álbum", R.drawable.ic_album),
    ;

    companion object {
        val INICIAL: Destino = CALCULADORA
    }
}
