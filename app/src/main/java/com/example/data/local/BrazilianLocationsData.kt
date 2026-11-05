package com.example.data.local

import java.text.Normalizer

data class BrazilianState(
    val uf: String,
    val name: String,
    val region: String
)

object BrazilianLocationsData {

    val STATES: List<BrazilianState> = listOf(
        BrazilianState("MG", "Minas Gerais", "Sudeste"),
        BrazilianState("SP", "São Paulo", "Sudeste"),
        BrazilianState("RJ", "Rio de Janeiro", "Sudeste"),
        BrazilianState("ES", "Espírito Santo", "Sudeste"),
        BrazilianState("PR", "Paraná", "Sul"),
        BrazilianState("SC", "Santa Catarina", "Sul"),
        BrazilianState("RS", "Rio Grande do Sul", "Sul"),
        BrazilianState("GO", "Goiás", "Centro-Oeste"),
        BrazilianState("DF", "Distrito Federal", "Centro-Oeste"),
        BrazilianState("MT", "Mato Grosso", "Centro-Oeste"),
        BrazilianState("MS", "Mato Grosso do Sul", "Centro-Oeste"),
        BrazilianState("BA", "Bahia", "Nordeste"),
        BrazilianState("PE", "Pernambuco", "Nordeste"),
        BrazilianState("CE", "Ceará", "Nordeste"),
        BrazilianState("MA", "Maranhão", "Nordeste"),
        BrazilianState("PB", "Paraíba", "Nordeste"),
        BrazilianState("RN", "Rio Grande do Norte", "Nordeste"),
        BrazilianState("AL", "Alagoas", "Nordeste"),
        BrazilianState("PI", "Piauí", "Nordeste"),
        BrazilianState("SE", "Sergipe", "Nordeste"),
        BrazilianState("PA", "Pará", "Norte"),
        BrazilianState("AM", "Amazonas", "Norte"),
        BrazilianState("RO", "Rondônia", "Norte"),
        BrazilianState("TO", "Tocantins", "Norte"),
        BrazilianState("AC", "Acre", "Norte"),
        BrazilianState("AP", "Amapá", "Norte"),
        BrazilianState("RR", "Roraima", "Norte")
    )

    private val CITIES_BY_UF: Map<String, List<String>> = mapOf(
        "MG" to listOf(
            "Monte Santo de Minas", "Itamogi", "Guaxupé", "Passos", "São Sebastião do Paraíso",
            "Poços de Caldas", "Arceburgo", "Alpinópolis", "Alterosa", "Alfenas", "Pouso Alegre",
            "Varginha", "Lavras", "Belo Horizonte", "Uberlândia", "Contagem", "Juiz de Fora",
            "Betim", "Montes Claros", "Uberaba", "Governador Valadares", "Ipatinga", "Sete Lagoas",
            "Divinópolis", "Santa Luzia", "Ibirité", "Patos de Minas", "Teófilo Otoni", "Barbacena",
            "Sabará", "Vespasiano", "Conselheiro Lafaiete", "Itabira", "Araguari", "Ubá",
            "Coronel Fabriciano", "Muriaé", "Ituiutaba", "Nova Serrana", "Itajubá", "Itaúna",
            "Pará de Minas", "Paracatu", "Caratinga", "Nova Lima", "São João del-Rei", "Patrocínio",
            "Timóteo", "Manhuaçu", "Unaí", "Curvelo", "João Monlevade", "Três Corações", "Viçosa",
            "Cataguases", "Ouro Preto", "Janaúba", "Januária", "Formiga", "Esmeraldas", "Pedro Leopoldo",
            "Ponte Nova", "Mariana", "Três Pontas", "Pirapora", "São Francisco", "Campo Belo",
            "Lagoa Santa", "Diamantina", "Leopoldina", "Bom Despacho", "Santos Dumont", "Oliveira",
            "Salinas", "Nanuque", "Bocaiúva", "Arcos", "Santa Rita do Sapucaí", "Machado",
            "Boa Esperança", "Muzambinho", "Guaranésia", "Areado", "Carmo do Rio Claro", "Cabo Verde"
        ),
        "SP" to listOf(
            "Mococa", "São Paulo", "Guarulhos", "Campinas", "São Bernardo do Campo", "Santo André",
            "Osasco", "São José dos Campos", "Ribeirão Preto", "Sorocaba", "Santos", "Mauá",
            "Diadema", "Carapicuíba", "Bauru", "Itaquaquecetuba", "São Vicente", "Praia Grande",
            "Jundiaí", "Franca", "Piracicaba", "Barueri", "Taubaté", "Limeira", "Suzano",
            "Taboão da Serra", "Sumaré", "Barretos", "Araraquara", "São Carlos", "Indaiatuba",
            "Cotia", "Marília", "Americana", "Jacareí", "Araras", "Hortolândia", "Rio Claro",
            "Araçatuba", "Santa Bárbara d'Oeste", "Bragança Paulista", "Itapetininga", "Botucatu",
            "São João da Boa Vista", "Casa Branca", "Tambaú", "Santa Cruz das Palmeiras",
            "Vargem Grande do Sul", "Caconde", "Tapiratiba", "São José do Rio Pardo",
            "Espírito Santo do Pinhal", "Itapira", "Mogi Guaçu", "Mogi Mirim", "Aguaí",
            "Leme", "Porto Ferreira", "Descalvado", "Pirassununga", "São Sebastião da Grama",
            "Divinolândia", "Presidente Prudente", "Jaú", "Catanduva", "Ourinhos", "Votuporanga",
            "Valinhos", "Atibaia", "Pindamonhangaba", "Guaratinguetá", "Caraguatatuba", "Ubatuba"
        ),
        "RJ" to listOf(
            "Rio de Janeiro", "São Gonçalo", "Duque de Caxias", "Nova Iguaçu", "Niterói",
            "Belford Roxo", "Campos dos Goytacazes", "São João de Meriti", "Petrópolis",
            "Volta Redonda", "Macaé", "Magé", "Itaboraí", "Cabo Frio", "Angra dos Reis",
            "Nova Friburgo", "Barra Mansa", "Teresópolis", "Mesquita", "Maricá", "Rio das Ostras",
            "Nilópolis", "Queimados", "Itaguaí", "Araruama", "Resende", "São Pedro da Aldeia",
            "Itaperuna", "Barra do Piraí", "Saquarema", "Seropédica", "Três Rios", "Valença"
        ),
        "PR" to listOf(
            "Curitiba", "Londrina", "Maringá", "Ponta Grossa", "Cascavel", "São José dos Pinhais",
            "Foz do Iguaçu", "Colombo", "Guarapuava", "Paranaguá", "Araucária", "Toledo",
            "Apucarana", "Pinhais", "Campo Largo", "Arapongas", "Almirante Tamandaré", "Umuarama",
            "Piraquara", "Cambé", "Fazenda Rio Grande", "Sarandi", "Campo Mourão", "Francisco Beltrão",
            "Paranavaí", "Pato Branco", "Cianorte", "Telêmaco Borba", "Castro", "Rolândia",
            "Irati", "União da Vitória", "Ibiporã", "Prudentópolis", "Cornélio Procópio"
        ),
        "RS" to listOf(
            "Porto Alegre", "Caxias do Sul", "Canoas", "Pelotas", "Santa Maria", "Gravataí",
            "Viamão", "Novo Hamburgo", "São Leopoldo", "Rio Grande", "Alvorada", "Passo Fundo",
            "Sapucaia do Sul", "Uruguaiana", "Santa Cruz do Sul", "Cachoeirinha", "Bento Gonçalves",
            "Bagé", "Erechim", "Guaíba", "Lajeado", "Ijuí", "Esteio", "Sapiranga", "Farroupilha",
            "Santana do Livramento", "Alegrete", "Camaquã", "Vacaria", "Cruz Alta", "Gramado", "Canela"
        ),
        "SC" to listOf(
            "Florianópolis", "Joinville", "Blumenau", "São José", "Chapecó", "Itajaí",
            "Criciúma", "Jaraguá do Sul", "Palhoça", "Lages", "Balneário Camboriú", "Brusque",
            "Tubarão", "São Bento do Sul", "Caçador", "Camboriú", "Navegantes", "Concórdia",
            "Rio do Sul", "Gaspar", "Biguaçu", "Indaial", "Itapema", "Mafra", "Canoinhas"
        ),
        "BA" to listOf(
            "Salvador", "Feira de Santana", "Vitória da Conquista", "Camaçari", "Juazeiro",
            "Itabuna", "Lauro de Freitas", "Ilhéus", "Jequié", "Teixeira de Freitas", "Alagoinhas",
            "Barreiras", "Porto Seguro", "Simões Filho", "Paulo Afonso", "Eunápolis",
            "Santo Antônio de Jesus", "Valença", "Candeias", "Guanambi", "Jacobina", "Serrinha",
            "Senhor do Bonfim", "Dias d'Ávila", "Luís Eduardo Magalhães", "Itapetinga", "Irecê"
        ),
        "GO" to listOf(
            "Goiânia", "Aparecida de Goiânia", "Anápolis", "Rio Verde", "Águas Lindas de Goiás",
            "Luziânia", "Valparaíso de Goiás", "Trindade", "Formosa", "Novo Gama", "Senador Canedo",
            "Catalão", "Itumbiara", "Jataí", "Caldas Novas", "Planaltina", "Santo Antônio do Descoberto",
            "Cidade Ocidental", "Goianésia", "Cristalina", "Mineiros", "Inhumas", "Jaraguá"
        ),
        "DF" to listOf(
            "Brasília", "Taguatinga", "Ceilândia", "Samambaia", "Plano Piloto", "Águas Claras",
            "Guará", "Gama", "Santa Maria", "Sobradinho", "São Sebastião", "Recanto das Emas",
            "Vicente Pires", "Riacho Fundo", "Planaltina", "Brazlândia", "Paranoá", "Núcleo Bandeirante"
        ),
        "CE" to listOf(
            "Fortaleza", "Caucaia", "Juazeiro do Norte", "Maracanaú", "Sobral", "Crato",
            "Itapipoca", "Maranguape", "Iguatu", "Quixadá", "Pacatuba", "Aquiraz", "Russas",
            "Canindé", "Tianguá", "Crateús", "Aracati", "Cascavel", "Pacajus", "Icó"
        ),
        "PE" to listOf(
            "Recife", "Jaboatão dos Guararapes", "Olinda", "Caruaru", "Petrolina", "Paulista",
            "Cabo de Santo Agostinho", "Camaragibe", "Garanhuns", "Vitória de Santo Antão",
            "Igarassu", "São Lourenço da Mata", "Abreu e Lima", "Ipojuca", "Serra Talhada",
            "Araripina", "Gravatá", "Carpina", "Belo Jardim", "Santa Cruz do Capibaribe"
        ),
        "ES" to listOf(
            "Vitória", "Vila Velha", "Serra", "Cariacica", "Cachoeiro de Itapemirim", "Linhares",
            "Colatina", "São Mateus", "Guarapari", "Aracruz", "Viana", "Nova Venécia",
            "Barra de São Francisco", "Marataízes", "Castelo", "Santa Maria de Jetibá"
        ),
        "MT" to listOf(
            "Cuiabá", "Várzea Grande", "Rondonópolis", "Sinop", "Tangará da Serra", "Sorriso",
            "Lucas do Rio Verde", "Primavera do Leste", "Barra do Garças", "Alta Floresta",
            "Pontes e Lacerda", "Nova Mutum", "Juína", "Campo Verde", "Cáceres"
        ),
        "MS" to listOf(
            "Campo Grande", "Dourados", "Três Lagoas", "Corumbá", "Ponta Porã", "Naviraí",
            "Nova Andradina", "Sidrolândia", "Aquidauana", "Maracaju", "Paranaíba", "Amambai"
        ),
        "PA" to listOf(
            "Belém", "Ananindeua", "Santarém", "Marabá", "Parauapebas", "Castanhal",
            "Abaetetuba", "Cametá", "Marituba", "Bragança", "São Félix do Xingu", "Altamira"
        ),
        "AM" to listOf(
            "Manaus", "Parintins", "Itacoatiara", "Manacapuru", "Coari", "Tabatinga",
            "Maués", "Tefé", "Manicoré", "Humaitá", "Iranduba", "São Gabriel da Cachoeira"
        ),
        "MA" to listOf(
            "São Luís", "Imperatriz", "São José de Ribamar", "Timon", "Caxias", "Codó",
            "Paço do Lumiar", "Açailândia", "Bacabal", "Balsas", "Santa Inês", "Barra do Corda"
        ),
        "PB" to listOf(
            "João Pessoa", "Campina Grande", "Santa Rita", "Patos", "Bayeux", "Sousa",
            "Cajazeiras", "Cabedelo", "Guarabira", "Mamanguape", "Queimadas", "São Bento"
        ),
        "RN" to listOf(
            "Natal", "Mossoró", "Parnamirim", "São Gonçalo do Amarante", "Macaíba", "Ceará-Mirim",
            "Caicó", "Açu", "Currais Novos", "São José de Mipibu", "Santa Cruz", "Nova Cruz"
        ),
        "AL" to listOf(
            "Maceió", "Arapiraca", "Rio Largo", "Palmeira dos Índios", "União dos Palmares",
            "Penedo", "São Miguel dos Campos", "Campo Alegre", "Coruripe", "Delmiro Gouveia"
        ),
        "PI" to listOf(
            "Teresina", "Parnaíba", "Picos", "Piripiri", "Floriano", "Barras",
            "Campo Maior", "União", "Altos", "Esperantina", "Pedro II", "Oeiras"
        ),
        "SE" to listOf(
            "Aracaju", "Nossa Senhora do Socorro", "Lagarto", "Itabaiana", "São Cristóvão",
            "Estância", "Tobias Barreto", "Simão Dias", "Itabaianinha", "Poço Redondo"
        ),
        "RO" to listOf(
            "Porto Velho", "Ji-Paraná", "Ariquemes", "Vilhena", "Cacoal", "Rolim de Moura",
            "Jaru", "Guajará-Mirim", "Machadinho d'Oeste", "Pimenta Bueno"
        ),
        "TO" to listOf(
            "Palmas", "Araguaína", "Gurupi", "Porto Nacional", "Paraíso do Tocantins",
            "Colinas do Tocantins", "Guaraí", "Tocantinópolis", "Dianópolis"
        ),
        "AC" to listOf(
            "Rio Branco", "Cruzeiro do Sul", "Sena Madureira", "Tarauacá", "Feijó",
            "Brasiléia", "Senador Guiomard", "Plácido de Castro", "Xapuri"
        ),
        "AP" to listOf(
            "Macapá", "Santana", "Laranjal do Jari", "Oiapoque", "Porto Grande",
            "Mazagão", "Tartarugalzinho", "Pedra Branca do Amapari"
        ),
        "RR" to listOf(
            "Boa Vista", "Rorainópolis", "Caracaraí", "Pacaraima", "Cantá",
            "Mucajaí", "Alto Alegre", "Bonfim", "São João da Baliza"
        )
    )

    private fun normalize(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    fun getCitiesForState(uf: String): List<String> {
        return CITIES_BY_UF[uf.uppercase()] ?: emptyList()
    }

    fun findStateForCity(cityName: String): BrazilianState? {
        val norm = normalize(cityName)
        for ((uf, cities) in CITIES_BY_UF) {
            if (cities.any { normalize(it) == norm }) {
                return STATES.firstOrNull { it.uf == uf }
            }
        }
        return null
    }

    /**
     * Busca cidades filtradas por estado e termo digitado (sem acentos e case-insensitive).
     * Se query for em branco, retorna as principais cidades do estado.
     */
    fun searchCities(uf: String, query: String, extraCities: List<String> = emptyList()): List<String> {
        val baseCities = getCitiesForState(uf)
        val combined = linkedSetOf<String>()
        combined.addAll(baseCities)
        combined.addAll(extraCities)

        val cleanQuery = normalize(query)
        if (cleanQuery.isBlank()) {
            return combined.toList().take(25)
        }

        return combined.filter { city ->
            normalize(city).contains(cleanQuery)
        }.sortedWith(compareBy(
            { !normalize(it).startsWith(cleanQuery) }, // Prioriza cidades que começam com o que foi digitado
            { it }
        )).take(30)
    }
}
