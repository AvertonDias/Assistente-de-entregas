package com.example.data.local

import com.example.data.local.entity.StreetCatalog

object MonteSantoStreetData {

    const val DEFAULT_CITY = "Monte Santo de Minas"
    const val DEFAULT_UF = "MG"
    const val DEFAULT_CEP = "37968-000"

    fun getInitialStreets(): List<StreetCatalog> {
        val city = DEFAULT_CITY
        val uf = DEFAULT_UF
        val cep = DEFAULT_CEP

        return listOf(
            // Centro e Principais Vias
            StreetCatalog(nome = "Coronel Antônio Paulino da Costa", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Doutor Pedro Paulino da Costa", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Sete de Setembro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Barão do Rio Branco", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Tiradentes", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Treze de Maio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Marechal Deodoro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Capitão Custódio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Major Juventino", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Sebastião", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Minas Gerais", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Paulo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Goiás", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Conde Ribeiro do Valle", tipo = "Avenida", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Vital Paulino da Costa", tipo = "Avenida", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Coronel Hermógenes", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Matriz", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Doutor Pedro Paulino da Costa", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Joaquim Ribeiro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Alferes João Ferreira", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Francisco Paulino", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Antônio Dias", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Silva Jardim", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Rui Barbosa", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Duque de Caxias", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Prudente de Morais", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Campos Salles", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Quintino Bocaiúva", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Benjamin Constant", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Floriano Peixoto", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "José Bonifácio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santos Dumont", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Getúlio Vargas", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),

            // Bairro São Sebastião / Vila Nova / Retiro das Rosas
            StreetCatalog(nome = "Bela Vista", tipo = "Rua", bairro = "São Sebastião", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "da Saudade", tipo = "Rua", bairro = "São Sebastião", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São José", tipo = "Rua", bairro = "São Sebastião", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santa Rita", tipo = "Rua", bairro = "São Sebastião", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santo Antônio", tipo = "Rua", bairro = "São Sebastião", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santa Cruz", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Nova", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Boa Esperança", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Flores", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Rosas", tipo = "Rua", bairro = "Retiro das Rosas", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "dos Cravos", tipo = "Rua", bairro = "Retiro das Rosas", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Orquídeas", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Violetas", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Hortênsias", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Margaridas", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),

            // Jardim Amélia / Morada do Sol / Bom Jesus
            StreetCatalog(nome = "Bom Jesus", tipo = "Rua", bairro = "Bom Jesus", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Vicente", tipo = "Rua", bairro = "Bom Jesus", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Nossa Senhora de Fátima", tipo = "Rua", bairro = "Bom Jesus", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Judas Tadeu", tipo = "Rua", bairro = "Morada do Sol", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "do Sol", tipo = "Rua", bairro = "Morada do Sol", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Alvorada", tipo = "Rua", bairro = "Morada do Sol", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Amélia Paulino", tipo = "Rua", bairro = "Jardim Amélia", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Esperança", tipo = "Rua", bairro = "Jardim Amélia", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Primavera", tipo = "Rua", bairro = "Jardim Amélia", cidade = city, uf = uf, cep = cep),

            // Distrito de Milagre e Acessos
            StreetCatalog(nome = "Principal de Milagre", tipo = "Rua", bairro = "Distrito de Milagre", cidade = city, uf = uf, cep = "37969-000"),
            StreetCatalog(nome = "da Matriz de Milagre", tipo = "Praça", bairro = "Distrito de Milagre", cidade = city, uf = uf, cep = "37969-000"),
            StreetCatalog(nome = "São Benedito", tipo = "Rua", bairro = "Distrito de Milagre", cidade = city, uf = uf, cep = "37969-000")
        )
    }
}
