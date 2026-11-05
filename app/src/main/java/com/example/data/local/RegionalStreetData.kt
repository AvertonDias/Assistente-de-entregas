package com.example.data.local

import com.example.data.local.entity.StreetCatalog

object RegionalStreetData {

    const val ITAMOGI_CITY = "Itamogi"
    const val ARCEBURGO_CITY = "Arceburgo"
    const val MOCOCA_CITY = "Mococa"

    fun getItamogiStreets(): List<StreetCatalog> {
        val city = ITAMOGI_CITY
        val uf = "MG"
        val cep = "37973-000"

        return listOf(
            // Centro e Principais Vias de Itamogi (MG)
            StreetCatalog(nome = "Coronel Francisco Dias", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Barão do Rio Branco", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Sete de Setembro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Tiradentes", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Treze de Maio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Minas Gerais", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Paulo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Rio de Janeiro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Goiás", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Espírito Santo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Bahia", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Paraná", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Floriano Peixoto", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Marechal Deodoro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Major João Paulino", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Doutor Pedro Paulino", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santa Rita", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "da Saudade", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São José", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Sebastião", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santo Antônio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Boa Esperança", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Bela Vista", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Getúlio Vargas", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "José Bonifácio", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Duque de Caxias", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Rui Barbosa", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Benjamin Constant", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Quintino Bocaiúva", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Campos Salles", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Monsenhor Agostinho", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "da Matriz", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Almerinda Barreto", tipo = "Avenida", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santa Cruz", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Nova", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Alferes João Ferreira", tipo = "Rua", bairro = "Vila Nova", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Primeiro de Maio", tipo = "Rua", bairro = "Vila Operária", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "dos Operários", tipo = "Rua", bairro = "Vila Operária", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Flores", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "das Rosas", tipo = "Rua", bairro = "Jardim das Flores", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Primavera", tipo = "Rua", bairro = "Jardim Primavera", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Esperança", tipo = "Rua", bairro = "Jardim Primavera", cidade = city, uf = uf, cep = cep)
        )
    }

    fun getArceburgoStreets(): List<StreetCatalog> {
        val city = ARCEBURGO_CITY
        val uf = "MG"
        val cep = "37965-000"

        return listOf(
            StreetCatalog(nome = "Doutor Bento de Godoy", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Coronel Cândido de Souza Dias", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São José", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Barão do Rio Branco", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Tiradentes", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Sete de Setembro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Minas Gerais", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Paulo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "da Matriz", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep)
        )
    }

    fun getMococaStreets(): List<StreetCatalog> {
        val city = MOCOCA_CITY
        val uf = "SP"
        val cep = "13730-000"

        return listOf(
            StreetCatalog(nome = "Coronel Diogo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Visconde do Rio Branco", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Quinze de Novembro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Marechal Deodoro", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Santa Cruz", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "São Paulo", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Minas Gerais", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Carmo Taliberti", tipo = "Rua", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Gabriel do Ó", tipo = "Avenida", bairro = "Centro", cidade = city, uf = uf, cep = cep),
            StreetCatalog(nome = "Marechal Deodoro", tipo = "Praça", bairro = "Centro", cidade = city, uf = uf, cep = cep)
        )
    }
}
