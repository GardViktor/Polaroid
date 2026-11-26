package com.polaroid.app.registros.banco

import android.content.Context
import androidx.core.content.edit
import com.polaroid.app.registros.ConfiguracaoTaxas
import com.polaroid.app.registros.FormaPagamento
import com.polaroid.app.registros.RepositorioConfiguracao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Taxas e limite de estoque baixo guardados no celular (SharedPreferences). */
class RepositorioConfiguracaoPrefs(context: Context) : RepositorioConfiguracao {

    private val prefs = context.applicationContext.getSharedPreferences("configuracao", Context.MODE_PRIVATE)
    private val atual = MutableStateFlow(ler())

    override fun observar(): Flow<ConfiguracaoTaxas> = atual

    override suspend fun salvar(configuracao: ConfiguracaoTaxas) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                FormaPagamento.entries.forEach { putInt(chave(it), configuracao.taxa(it)) }
                putInt(LIMITE, configuracao.limiteEstoqueBaixo)
            }
        }
        atual.value = configuracao
    }

    private fun ler(): ConfiguracaoTaxas = ConfiguracaoTaxas(
        pontosBase = FormaPagamento.entries.associateWith { prefs.getInt(chave(it), it.taxaPadraoPontosBase) },
        limiteEstoqueBaixo = prefs.getInt(LIMITE, ConfiguracaoTaxas.PADRAO.limiteEstoqueBaixo),
    )

    private fun chave(forma: FormaPagamento) = "taxa_${forma.name}"

    private companion object {
        const val LIMITE = "limite_estoque_baixo"
    }
}
