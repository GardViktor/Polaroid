package com.polaroid.buscador

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Títulos reais coletados nas buscas por "filme instax mini" (nov/2026). */
class FiltroCompatibilidadeTest {

    private fun aceita(titulo: String) = assertTrue(FiltroCompatibilidade.compativel(titulo), "deveria aceitar: $titulo")
    private fun rejeita(titulo: String) = assertFalse(FiltroCompatibilidade.compativel(titulo), "deveria rejeitar: $titulo")

    @Test
    fun `aceita filmes instax mini de qualquer cor ou edicao`() {
        listOf(
            "Filme Fujifilm Instax Mini 10 Fotos",
            "Filme Fujifilm Instax Mini 20 Fotos",
            "Filme Fujifilm Instax Mini Branco 20 Fotos, 54 X 86 Mm, Iso 800",
            "Filme Fujifilm Instax Mini Monoch 10 Fotos",
            "Filme Fujifilm Instax Mini Rainbow 10 Fotos",
            "Filme instantâneo Instax Mini Fujifilm 20 fotos",
            "Filme Instax  Fujifilm Mini Rainbow Com 10 Fotos  Fujifilm",
            "Filme Instax Mini Pink Lemonade - 10 Fotos Fujifilm",
            "Filme Instax Mini Sky Blue - 10 Fotos Fujifilm",
            "Filme para Instax Mini com 10 Fotos - Branco",
            "FILME INSTAX MINI MONOCHROME 10 POSES",
            "Kit 3 Filmes Instax Mini 10 Fotos Cada",
            "Filme Para Instax Mini 12 - 20 Fotos",
        ).forEach(::aceita)
    }

    @Test
    fun `para camera ou para impressora indica compatibilidade, nao o produto`() {
        listOf(
            "Filme Para Câmera Instax Mini Spray Art, 10 Fotos, 705068238",
            "Filme para câmera Fujifilm Instax Mini SPRINKLES - 10 fotos",
            "Kit 60 Fotos Filme Instax Mini Para câmeras e Impressoras Instax Mini",
            "Filme Instax Mini 20 Fotos + 20 Fotos Black Com Caixa De Presente",
        ).forEach(::aceita)
        // Continua rejeitando quando o produto é a câmera ou vem com acessório
        rejeita("Kit Camera Instax Mini 12 + Bolsa + Filme 10 Foto Cor Azul")
        rejeita("Álbum para Camera Instax Mini + Filme 20 fotos Instax Mini")
    }

    @Test
    fun `rejeita cameras e kits com camera`() {
        listOf(
            "Câmera Instantânea Fujifilm Instax Mini 12 - Azul Candy",
            "Câmera Fotográfica Fujifilm Instax Mini 12 com Operação Fácil e Modo Close-Up Branco",
            "Câmera Instantânea Fujifilm instax mini 41™ Preta",
            "Kit Câmera Instax Mini 12 com pack 10 fotos Mermaid Tail e Bolsa Azul Fujifilm",
            "Kit Câmera instantânea Fujifilm instax mini 13™ Clay White + Pack 10 fotos Pastel Galaxy + Bolsa White Tie Dye + Photo tag",
            "Kit Câmera Instax Mini Fujifilm 12 Verde com Bolsa + 10 Filmes",
            "Camera Instax Mini 12 Fujifilm",
        ).forEach(::rejeita)
    }

    @Test
    fun `rejeita impressoras e outros aparelhos`() {
        listOf(
            "Impressora Fotográfica Fujifilm Instax Mini Link 3 Bluetooth Rosa",
            "Impressora para Smartphone Instax Mini Link 3 Fujifilm - Clay White",
            "Câmera e Impressora para Smartphone Fujifilm instax mini Evo Black",
        ).forEach(::rejeita)
    }

    @Test
    fun `rejeita outros formatos de filme`() {
        listOf(
            "Filme Instax Square 10 Fotos Fujifilm",
            "Filme Instax Wide 20 Fotos",
            "Filme Polaroid i-Type Color 8 Fotos",
            "Filme Polaroid 600 Preto e Branco",
            "Filme Polaroid Go Color Double Pack",
        ).forEach(::rejeita)
    }

    @Test
    fun `rejeita acessorios`() {
        listOf(
            "Álbum de Fotos Instax Mini 64 Fotos",
            "Capa Case Protetora Para Instax Mini 12",
            "Porta-Retrato Instax Mini Kit 10 Unidades",
            "Bolsa Para Câmera Instax Mini",
        ).forEach(::rejeita)
    }

    @Test
    fun `rejeita produtos sem relacao`() {
        listOf(
            "Brinquedo de Montar Lego 43268 Casa de Praia de 2 Níveis com Minifiguras e Acessórios do Filme Lilo & Stitch Plástico",
            "Mini Figura Surpresa - Minecraft O Filme - Sortido - Mattel",
            "Projetor Mini LED Portátil MDM LHY320 para Filmes e Jogos com Resolução 1080p e Suporte Ajustável",
            "Bolsa Térmica Dobrável TRC1117 Isolamento Interno Filme Aluminizado Zíper Alças Reforçadas Top Rio Comercial",
            "Miniatura Carro Pagani Velozes E Furiosos Carros De Filme",
        ).forEach(::rejeita)
    }

    @Test
    fun `sem instax no titulo so aceita de fonte confiavel`() {
        // Loja oficial da Fujifilm, coleção "filme-mini": o título não repete "Instax"
        rejeita("Mini Branco 10 Fotos")
        assertTrue(FiltroCompatibilidade.compativel("Mini Branco 10 Fotos", categoriaGarantida = true))
        // Mesmo de fonte confiável, aparelho continua fora
        assertFalse(FiltroCompatibilidade.compativel("Câmera Instax Mini 12", categoriaGarantida = true))
    }
}
