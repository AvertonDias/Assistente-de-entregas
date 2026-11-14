package com.example

import com.example.util.AddressNormalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressNormalizerTest {

    @Test
    fun `matches incomplete street name like Vital for Avenida Vital Paulino da Costa`() {
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Vital",
                "Avenida Vital Paulino da Costa"
            )
        )
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Avenida Vital Paulino da Costa",
                "Rua Vital"
            )
        )
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Vital Paulino",
                "Avenida Vital Paulino da Costa"
            )
        )
        assertTrue(
            AddressNormalizer.matchesPrecise(
                "Vital, 120",
                "Avenida Vital Paulino da Costa",
                "120"
            )
        )
    }

    @Test
    fun `matches reordered street words like Francisco Antonio vs Antonio Francisco`() {
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Rua Antônio Francisco da Luz",
                "Rua Francisco Antônio da Luz"
            )
        )
        assertTrue(
            AddressNormalizer.matchesPrecise(
                "Rua Antônio Francisco da Luz, 50",
                "Rua Francisco Antônio da Luz",
                "50"
            )
        )
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Rua Antônio da Luz",
                "Rua Francisco Antônio da Luz"
            )
        )
        assertTrue(
            AddressNormalizer.areStreetNamesMatching(
                "Rua F. Antônio da Luz",
                "Rua Francisco Antônio da Luz"
            )
        )
    }

    @Test
    fun `ranks exact match higher than partial match`() {
        val exactScore = AddressNormalizer.scoreStreetNamesMatch(
            "Rua Vital",
            "Rua Vital"
        )
        val reorderedScore = AddressNormalizer.scoreStreetNamesMatch(
            "Rua Antônio Francisco da Luz",
            "Rua Francisco Antônio da Luz"
        )
        val partialTwoWordsScore = AddressNormalizer.scoreStreetNamesMatch(
            "Vital Paulino",
            "Avenida Vital Paulino da Costa"
        )
        val partialOneWordScore = AddressNormalizer.scoreStreetNamesMatch(
            "Vital",
            "Avenida Vital Paulino da Costa"
        )

        assertTrue(exactScore > reorderedScore)
        assertTrue(reorderedScore > partialTwoWordsScore)
        assertTrue(partialTwoWordsScore > partialOneWordScore)
        assertTrue(partialOneWordScore > 0)
    }

    @Test
    fun `does not false positive on distinct streets or distinct numbers`() {
        assertFalse(
            AddressNormalizer.areStreetNamesMatching(
                "Rua São Paulo",
                "Rua São Pedro"
            )
        )
        assertFalse(
            AddressNormalizer.areStreetNamesMatching(
                "Rua José da Silva",
                "Rua José de Souza"
            )
        )
        assertFalse(
            AddressNormalizer.areStreetNamesMatching(
                "Rua 101",
                "Rua 102"
            )
        )
        assertFalse(
            AddressNormalizer.matchesPrecise(
                "Vital, 120",
                "Avenida Vital Paulino da Costa",
                "999"
            )
        )
    }
}
