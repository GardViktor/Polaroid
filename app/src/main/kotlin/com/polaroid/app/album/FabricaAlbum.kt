package com.polaroid.app.album

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Clock

/** Monta o AlbumViewModel com as implementações reais do Android. */
val FabricaAlbumViewModel = viewModelFactory {
    initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        AlbumViewModel(
            repositorio = RepositorioFotosMediaStore(app),
            localizacao = ProvedorLocalizacaoAndroid(app),
            nomeLocal = ProvedorNomeLocalGeocoder(app),
            relogio = Clock.systemDefaultZone(),
        )
    }
}
