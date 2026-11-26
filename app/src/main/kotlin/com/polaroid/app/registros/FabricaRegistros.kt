package com.polaroid.app.registros

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polaroid.app.registros.banco.BancoPolaroid
import com.polaroid.app.registros.banco.RepositorioRegistrosRoom
import java.time.Clock

/** Mesmo ViewModel (escopo da Activity) usado pela calculadora e pela aba Relatórios. */
val FabricaRegistrosViewModel = viewModelFactory {
    initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        RegistrosViewModel(
            repositorio = RepositorioRegistrosRoom(BancoPolaroid.obter(app).registros()),
            relogio = Clock.systemDefaultZone(),
        )
    }
}
