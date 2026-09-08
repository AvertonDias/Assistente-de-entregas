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
    assertEquals("1.843", parsed3.number)
    val ext3 = AddressNormalizer.extractStreetAndNumber(case3)
    assertEquals("Av Limirio Pereira de Melo, 1.843", ext3)
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
}
