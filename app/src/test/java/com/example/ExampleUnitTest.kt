package com.example

import com.example.data.local.entity.Person
import com.example.util.AddressNormalizer
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testRuaDosTrabalhadoresDoesNotExtractTorreAbalhadores() {
    val comp = AddressNormalizer.extractComplement("Rua dos Trabalhadores")
    assertEquals("", comp)

    val compWithNumber = AddressNormalizer.extractComplement("Trabalhadores 115")
    assertEquals("", compWithNumber)

    val parsed1 = AddressNormalizer.parseAddressComponents("Rua dos Trabalhadores", "160", "", "")
    assertEquals("Rua dos Trabalhadores", parsed1.street)
    assertEquals("160", parsed1.number)
    assertEquals("", parsed1.complement)
    assertEquals(false, parsed1.hasComplement)

    val parsed2 = AddressNormalizer.parseAddressComponents("Rua dos Trabalhadores, 320")
    assertEquals("Rua dos Trabalhadores", parsed2.street)
    assertEquals("320", parsed2.number)
    assertEquals("", parsed2.complement)

    val parsedWithTorre = AddressNormalizer.parseAddressComponents("Rua das Flores, 100 Torre A")
    assertEquals("Torre A", parsedWithTorre.complement)
  }

  @Test
  fun testExtractStreetAndNumber() {
    val res1 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores, 115")
    assertEquals("Rua dos Trabalhadores, 115", res1)

    val res2 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores 115")
    assertEquals("Rua dos Trabalhadores, 115", res2)

    val res3 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores, Nº 115")
    assertEquals("Rua dos Trabalhadores, 115", res3)

    val res4 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores nº 115")
    assertEquals("Rua dos Trabalhadores, 115", res4)

    val res5 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores - 115")
    assertEquals("Rua dos Trabalhadores, 115", res5)

    val res6 = AddressNormalizer.extractStreetAndNumber("Rua 15 de Novembro, 120")
    assertEquals("Rua 15 de Novembro, 120", res6)

    val res7 = AddressNormalizer.extractStreetAndNumber("Avenida Brasil, 450 - Centro")
    assertEquals("Avenida Brasil, 450", res7)

    val res8 = AddressNormalizer.extractStreetAndNumber("Rua dos Trabalhadores; 115; Centro; Cidade; CEP")
    assertEquals("Rua dos Trabalhadores, 115", res8)

    val res9 = AddressNormalizer.extractStreetAndNumber("Rua das Flores, S/N")
    assertEquals("Rua das Flores, S/N", res9)

    // Testes das duas telas reais de entrega:
    // 1. Tela 'Seleção' (com DETALHES, Bairro e Cidade/UF)
    val screen1 = "DETALHES:\nAV DR PEDRO PAULINO DA COSTA, 162, - CENTRO - Monte Santo de Minas/MG"
    val resScreen1 = AddressNormalizer.extractStreetAndNumber(screen1)
    assertEquals("Av Dr Pedro Paulino da Costa, 162", resScreen1)

    // 2. Tela 'LOEC' (Próxima Entrega, Depois e números com zeros)
    val screen2Next = "Próxima Entrega:\nAV DR PEDRO PAULINO DA COSTA, 162, - CENTRO - Monte Santo de Minas/MG"
    val resScreen2Next = AddressNormalizer.extractStreetAndNumber(screen2Next)
    assertEquals("Av Dr Pedro Paulino da Costa, 162", resScreen2Next)

    val screen2Depois = "Depois:\nRUA DOUTOR PEDRO PAULINO DA COSTA, 91 - CENTRO - Monte Santo de Minas/MG"
    val resScreen2Depois = AddressNormalizer.extractStreetAndNumber(screen2Depois)
    assertEquals("Rua Doutor Pedro Paulino da Costa, 91", resScreen2Depois)

    val screen2ZeroNum = "R AREIAO, 00783 - B AREIAO - Monte Santo de Minas/MG"
    val resScreen2ZeroNum = AddressNormalizer.extractStreetAndNumber(screen2ZeroNum)
    assertEquals("R Areiao, 00783", resScreen2ZeroNum)

    // Verificação de equivalência com cadastro no banco
    val matchNext = AddressNormalizer.matchesPrecise(screen2Next, "AV DR PEDRO PAULINO DA COSTA", "162")
    assertEquals(true, matchNext)

    val matchDepois = AddressNormalizer.matchesPrecise(screen2Depois, "RUA DR PEDRO PAULINO DA COSTA", "91")
    assertEquals(true, matchDepois)

    val matchZero = AddressNormalizer.matchesPrecise(screen2ZeroNum, "RUA AREIAO", "783")
    assertEquals(true, matchZero)
  }

  @Test
  fun testAddressSearchMatching() {
    val rawAddress = "Rua dos Trabalhadores, 115"
    val p1 = Person(id = 1, nome = "Primeiro", documento = "111", endereco = "Rua dos Trabalhadores", numero = "10")
    val p2 = Person(id = 2, nome = "Segundo", documento = "222", endereco = "Rua dos Trabalhadores", numero = "115")
    val p3 = Person(id = 3, nome = "Terceiro", documento = "333", endereco = "Rua dos Trabalhadores, 10", numero = "")

    println("p1 match: " + AddressNormalizer.matchesPrecise(rawAddress, p1.endereco, p1.numero, p1.complemento))
    println("p2 match: " + AddressNormalizer.matchesPrecise(rawAddress, p2.endereco, p2.numero, p2.complemento))
    println("p3 match: " + AddressNormalizer.matchesPrecise(rawAddress, p3.endereco, p3.numero, p3.complemento))
    println("p1 alt match: " + AddressNormalizer.matchesPrecise(rawAddress, "${p1.endereco}, ${p1.numero}", p1.numero, p1.complemento))
    println("p2 alt match: " + AddressNormalizer.matchesPrecise(rawAddress, "${p2.endereco}, ${p2.numero}", p2.numero, p2.complemento))
    println("p3 alt match: " + AddressNormalizer.matchesPrecise(rawAddress, "${p3.endereco}, ${p3.numero}", p3.numero, p3.complemento))

    val parsedRaw = AddressNormalizer.parseAddressComponents(rawAddress)
    println("parsedRaw: street='${parsedRaw.street}', number='${parsedRaw.number}', complement='${parsedRaw.complement}'")
  }

  @Test
  fun testFourImageAddressCases() {
    // Caso 1: Jd 1 de Maio;Av Limirio Pereira de Melo;2071 2071, 1 - Monte Santo de Minas/MG
    val case1 = "Jd 1 de Maio;Av Limirio Pereira de Melo;2071 2071, 1 - Monte Santo de Minas/MG"
    val parsed1 = AddressNormalizer.parseAddressComponents(case1)
    assertEquals("Av Limirio Pereira de Melo", parsed1.street)
    assertEquals("2071", parsed1.number)
    assertEquals("Jd 1 de Maio", parsed1.neighborhood)
    val ext1 = AddressNormalizer.extractStreetAndNumber(case1)
    assertEquals("Av Limirio Pereira de Melo, 2071", ext1)
    assertTrue(AddressNormalizer.matchesPrecise(case1, "Av Limirio Pereira de Melo", "2071"))
    assertTrue(AddressNormalizer.matchesPrecise(case1, "Avenida Limirio Pereira de Melo", "2071"))
    assertTrue(AddressNormalizer.matchesPrecise(case1, "Av Limirio Pereira de Melo, 2071", ""))

    // Caso 2: bela vista;José Augusto filho;200 oficina luz tech preta, 200 - Monte Santo de Minas/MG
    val case2 = "bela vista;José Augusto filho;200 oficina luz tech preta, 200 - Monte Santo de Minas/MG"
    val parsed2 = AddressNormalizer.parseAddressComponents(case2)
    assertEquals("José Augusto Filho", parsed2.street)
    assertEquals("200", parsed2.number)
    assertEquals("Bela Vista", parsed2.neighborhood)
    val ext2 = AddressNormalizer.extractStreetAndNumber(case2)
    assertEquals("José Augusto Filho, 200", ext2)
    assertTrue(AddressNormalizer.matchesPrecise(case2, "Rua José Augusto Filho", "200"))
    assertTrue(AddressNormalizer.matchesPrecise(case2, "José Augusto Filho", "200"))
    assertTrue(AddressNormalizer.matchesPrecise(case2, "R. Jose Augusto Filho", "200"))

    // Caso 3: Av Limirio Pereira de Melo, 1.843 - Sem Bairro - Monte Santo de Minas/MG
    val case3 = "Av Limirio Pereira de Melo, 1.843 - Sem Bairro - Monte Santo de Minas/MG"
    val parsed3 = AddressNormalizer.parseAddressComponents(case3)
    assertEquals("Av Limirio Pereira de Melo", parsed3.street)
    assertEquals("1843", parsed3.number)
    val ext3 = AddressNormalizer.extractStreetAndNumber(case3)
    assertEquals("Av Limirio Pereira de Melo, 1843", ext3)
    assertTrue(AddressNormalizer.matchesPrecise(case3, "Av Limirio Pereira de Melo", "1843"))
    assertTrue(AddressNormalizer.matchesPrecise(case3, "Av Limirio Pereira de Melo", "1.843"))
    assertTrue(AddressNormalizer.matchesPrecise(case3, "Avenida Limírio Pereira de Melo", "1843"))

    // Caso 4: matadouro;São Miguel;404 casa, 404 - Monte Santo de Minas/MG
    val case4 = "matadouro;São Miguel;404 casa, 404 - Monte Santo de Minas/MG"
    val parsed4 = AddressNormalizer.parseAddressComponents(case4)
    assertEquals("São Miguel", parsed4.street)
    assertEquals("404", parsed4.number)
    assertEquals("Matadouro", parsed4.neighborhood)
    val ext4 = AddressNormalizer.extractStreetAndNumber(case4)
    assertEquals("São Miguel, 404", ext4)
    assertTrue(AddressNormalizer.matchesPrecise(case4, "Rua São Miguel", "404"))
    assertTrue(AddressNormalizer.matchesPrecise(case4, "São Miguel", "404"))
    assertTrue(AddressNormalizer.matchesPrecise(case4, "R. Sao Miguel", "404"))
  }

  @Test
  fun testDateAndRomanNumeralAddresses() {
    // 1. Não deve tratar o "15" em "15 de Julho" como número da casa se não houver outro número
    val parsedStreetOnly15 = AddressNormalizer.parseAddressComponents("15 de Julho")
    assertEquals("15 de Julho", parsedStreetOnly15.street)
    assertEquals("", parsedStreetOnly15.number)

    val parsedStreetOnlyXV = AddressNormalizer.parseAddressComponents("XV de Julho")
    assertEquals("Xv de Julho", parsedStreetOnlyXV.street)
    assertEquals("", parsedStreetOnlyXV.number)

    // 2. Extração de número de imóvel quando a rua contém data
    val parsedWithNumber1 = AddressNormalizer.parseAddressComponents("15 de Julho, 120")
    assertEquals("15 de Julho", parsedWithNumber1.street)
    assertEquals("120", parsedWithNumber1.number)

    val parsedWithNumber2 = AddressNormalizer.parseAddressComponents("Rua XV de Julho, 120")
    assertEquals("Rua Xv de Julho", parsedWithNumber2.street)
    assertEquals("120", parsedWithNumber2.number)

    val parsedWithNumber3 = AddressNormalizer.parseAddressComponents("XV de Julho 120")
    assertEquals("Xv de Julho", parsedWithNumber3.street)
    assertEquals("120", parsedWithNumber3.number)

    // 3. Normalização canônica equivalendo "XV de Julho", "15 de Julho" e "Quinze de Julho"
    val norm1 = AddressNormalizer.normalize("XV de Julho")
    val norm2 = AddressNormalizer.normalize("15 de Julho")
    val norm3 = AddressNormalizer.normalize("Quinze de Julho")
    assertEquals("15 DE JULHO", norm1)
    assertEquals("15 DE JULHO", norm2)
    assertEquals("15 DE JULHO", norm3)

    // 4. Correspondência precisa cruzada (banco tem "Rua 15 de Julho", tela traz "XV de Julho, 120")
    assertTrue(AddressNormalizer.matchesPrecise("XV de Julho, 120", "Rua 15 de Julho", "120"))
    assertTrue(AddressNormalizer.matchesPrecise("15 de Julho, 120", "Rua XV de Julho", "120"))
    assertTrue(AddressNormalizer.matchesPrecise("Rua XV de Julho, 120", "15 de Julho", "120"))
    assertTrue(AddressNormalizer.matchesPrecise("Rua Quinze de Julho, 120", "15 de Julho", "120"))

    // 5. Números diferentes de casa NÃO devem corresponder mesmo na mesma rua
    assertFalse(AddressNormalizer.matchesPrecise("XV de Julho, 120", "Rua 15 de Julho", "130"))
    assertFalse(AddressNormalizer.matchesPrecise("15 de Julho, 120", "Rua XV de Julho", "15"))

    // 6. Testes com outras datas históricas comuns
    assertEquals("7 DE SETEMBRO", AddressNormalizer.normalize("7 de Setembro"))
    assertEquals("7 DE SETEMBRO", AddressNormalizer.normalize("VII de Setembro"))
    assertEquals("7 DE SETEMBRO", AddressNormalizer.normalize("Sete de Setembro"))
    assertTrue(AddressNormalizer.matchesPrecise("VII de Setembro, 50", "Rua 7 de Setembro", "50"))

    assertEquals("24 DE MAIO", AddressNormalizer.normalize("24 de Maio"))
    assertEquals("24 DE MAIO", AddressNormalizer.normalize("XXIV de Maio"))
    assertEquals("24 DE MAIO", AddressNormalizer.normalize("Vinte e Quatro de Maio"))
    assertTrue(AddressNormalizer.matchesPrecise("XXIV de Maio, 80", "Rua 24 de Maio", "80"))

    assertEquals("1 DE MAIO", AddressNormalizer.normalize("1º de Maio"))
    assertEquals("1 DE MAIO", AddressNormalizer.normalize("Primeiro de Maio"))
    assertEquals("1 DE MAIO", AddressNormalizer.normalize("1 de Maio"))
    assertTrue(AddressNormalizer.matchesPrecise("1º de Maio, 30", "Rua Primeiro de Maio", "30"))

    // 7. Extração de números não deve capturar o dia da data
    assertEquals(emptyList<String>(), AddressNormalizer.extractNumbers("15 de Julho"))
    assertEquals(listOf("120"), AddressNormalizer.extractNumbers("15 de Julho, 120"))
    assertEquals(listOf("120"), AddressNormalizer.extractNumbers("XV de Julho, 120"))
  }

  @Test
  fun testAddressComplementRecognitionAndDisambiguation() {
    // 1. Extração de complemento em endereços com Casa 1, Casa 2, Apto 101, Fundos
    val parsed1 = AddressNormalizer.parseAddressComponents("Rua das Flores, 123 Casa 1")
    assertEquals("Rua das Flores", parsed1.street)
    assertEquals("123", parsed1.number)
    assertEquals("Casa 1", parsed1.complement)
    assertTrue(parsed1.hasComplement)

    val parsed2 = AddressNormalizer.parseAddressComponents("Rua das Flores, 123 Casa 2")
    assertEquals("Casa 2", parsed2.complement)

    val parsedFundos = AddressNormalizer.parseAddressComponents("Rua das Flores, 123 Fundos")
    assertEquals("Fundos", parsedFundos.complement)

    // 2. Quando a tela NÃO tem complemento (apenas "Rua das Flores, 123"):
    // matchesPrecise com banco que tem complemento ("Casa 1", "Casa 2") deve retornar TRUE,
    // permitindo que o assistente liste todas as casas daquele endereço para o usuário escolher!
    assertTrue(AddressNormalizer.matchesPrecise("Rua das Flores, 123", "Rua das Flores", "123", "Casa 1"))
    assertTrue(AddressNormalizer.matchesPrecise("Rua das Flores, 123", "Rua das Flores", "123", "Casa 2"))
    assertTrue(AddressNormalizer.matchesPrecise("Rua das Flores, 123", "Rua das Flores", "123", "Fundos"))

    // 3. Quando a tela ESPECIFICA complemento ("Rua das Flores, 123 Casa 1"):
    // Deve corresponder com Casa 1, mas NÃO deve corresponder com Casa 2!
    assertTrue(AddressNormalizer.matchesPrecise("Rua das Flores, 123 Casa 1", "Rua das Flores", "123", "Casa 1"))
    assertFalse(AddressNormalizer.matchesPrecise("Rua das Flores, 123 Casa 1", "Rua das Flores", "123", "Casa 2"))

    // 4. extractCleanAddress preserva o complemento se presente
    val cleanWithComp = AddressNormalizer.extractCleanAddress("Rua das Flores, 123 Casa 1")
    assertTrue(cleanWithComp.contains("Casa 1"))

    // 5. extractStreetAndNumber retorna a base limpa
    val cleanBase = AddressNormalizer.extractStreetAndNumber("Rua das Flores, 123 Casa 1")
    assertEquals("Rua das Flores, 123", cleanBase)
  }

  @Test
  fun testSearchAntenor663NotRegistered() {
    assertFalse(AddressNormalizer.matchesPrecise("Antenor 663", "Primeiro de Maio", "66"))
    assertFalse(AddressNormalizer.matchesPrecise("Antenor 663", "Primeiro de Maio", "136"))
    assertFalse(AddressNormalizer.matches("Antenor 663", "Primeiro de Maio"))
  }
}

