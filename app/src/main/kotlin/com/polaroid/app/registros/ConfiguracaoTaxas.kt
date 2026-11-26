package com.polaroid.app.registros

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

/** Taxas por forma de pagamento (pontos-base: 199 = 1,99%) e limite do aviso de estoque baixo. */
data class ConfiguracaoTaxas(
    val pontosBase: Map<FormaPagamento, Int> = emptyMap(),
    val limiteEstoqueBaixo: Int = 10,
) {
    fun taxa(forma: FormaPagamento): Int = pontosBase[forma] ?: forma.taxaPadraoPontosBase

    companion object {
        val PADRAO = ConfiguracaoTaxas()
    }
}

interface RepositorioConfiguracao {
    fun observar(): Flow<ConfiguracaoTaxas>
    suspend fun salvar(configuracao: ConfiguracaoTaxas)
}

/** Usado nos testes e como padrão quando não há armazenamento. */
class RepositorioConfiguracaoMemoria(inicial: ConfiguracaoTaxas = ConfiguracaoTaxas.PADRAO) : RepositorioConfiguracao {
    private val atual = MutableStateFlow(inicial)
    override fun observar(): Flow<ConfiguracaoTaxas> = atual
    override suspend fun salvar(configuracao: ConfiguracaoTaxas) {
        atual.value = configuracao
    }
}

/** Converte o texto digitado ("1,99") em pontos-base (199) e vice-versa. */
object TextoTaxa {
    private val FORMATO = Regex("""^\d{1,3}([.,]\d{1,2})?$""")

    fun paraPontosBase(texto: String): Int? {
        if (!FORMATO.matches(texto)) return null
        val pontos = BigDecimal(texto.replace(',', '.')).movePointRight(2).intValueExact()
        return pontos.takeIf { it <= 10_000 }
    }

    fun dePontosBase(pontosBase: Int): String = BigDecimal.valueOf(pontosBase.toLong(), 2).toPlainString().replace('.', ',')
}
