package com.polaroid.app.registros

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Formas de pagamento; a taxa padrão é gravada na venda no momento do registro. */
enum class FormaPagamento(val rotulo: String, val taxaPadraoPontosBase: Int) {
    PIX("Pix", 0),
    DINHEIRO("Dinheiro", 0),
    DEBITO("Débito", 199), // 1,99%
    CREDITO("Crédito", 499), // 4,99%
}

/**
 * Movimentos do estoque de fotos. Dinheiro sempre em centavos (Long): exato, sem arredondar.
 * [id] = 0 indica um registro ainda não salvo.
 */
sealed interface Registro {
    val id: Long
    val dataHora: Instant
    val quantidadeFotos: Int

    data class Compra(
        override val id: Long,
        override val dataHora: Instant,
        override val quantidadeFotos: Int,
        val valorPagoCentavos: Long,
    ) : Registro

    data class Venda(
        override val id: Long,
        override val dataHora: Instant,
        override val quantidadeFotos: Int,
        val precoPorFotoCentavos: Long,
        val formaPagamento: FormaPagamento,
        /** Taxa em pontos-base (199 = 1,99%), congelada no momento da venda. */
        val taxaPontosBase: Int,
    ) : Registro

    data class Perda(
        override val id: Long,
        override val dataHora: Instant,
        override val quantidadeFotos: Int,
    ) : Registro
}

interface RepositorioRegistros {
    fun observar(): Flow<List<Registro>>

    /** Insere (id = 0) ou atualiza (id existente). */
    suspend fun salvar(registro: Registro)

    suspend fun apagar(registro: Registro)
}
