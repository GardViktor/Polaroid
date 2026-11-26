package com.polaroid.app.registros

import com.polaroid.app.registros.banco.RegistroEntidade
import com.polaroid.app.registros.banco.paraEntidade
import com.polaroid.app.registros.banco.paraRegistro
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Garante que nada se perde ao gravar e ler do banco de dados. */
class ConversaoRegistroTest {

    private val momento = Instant.parse("2026-11-26T17:32:00Z")

    @Test
    fun `compra ida e volta`() {
        val compra = Registro.Compra(5, momento, 40, 12050)
        assertEquals(compra, compra.paraEntidade().paraRegistro())
    }

    @Test
    fun `venda ida e volta com forma e taxa`() {
        val venda = Registro.Venda(6, momento, 3, 1050, FormaPagamento.CREDITO, 350)
        assertEquals(venda, venda.paraEntidade().paraRegistro())
    }

    @Test
    fun `perda ida e volta`() {
        val perda = Registro.Perda(7, momento, 2)
        assertEquals(perda, perda.paraEntidade().paraRegistro())
    }

    @Test
    fun `entidade guarda o tipo e os centavos`() {
        val entidade = Registro.Venda(0, momento, 3, 1050, FormaPagamento.PIX, 0).paraEntidade()
        assertEquals(
            RegistroEntidade(0, "VENDA", momento.toEpochMilli(), 3, 1050, "PIX", 0),
            entidade,
        )
    }
}
