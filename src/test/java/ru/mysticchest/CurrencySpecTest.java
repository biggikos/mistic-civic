package ru.mysticchest;

import org.junit.jupiter.api.Test;
import ru.mysticchest.economy.CurrencySpec;

import static org.junit.jupiter.api.Assertions.*;

class CurrencySpecTest {
    @Test
    void plainCurrencyUsesDefaultProvider() {
        CurrencySpec s = CurrencySpec.parse("gold");
        assertNull(s.provider);
        assertEquals("gold", s.currency);
        assertEquals("gold", s.display);
    }

    @Test
    void providerPrefix() {
        CurrencySpec s = CurrencySpec.parse("excellenteconomy:coins");
        assertEquals("excellenteconomy", s.provider);
        assertEquals("coins", s.currency);
        assertEquals("excellenteconomy", CurrencySpec.parse("EE:coins").provider);
        assertEquals("coinsengine", CurrencySpec.parse("coins_engine:gems").provider);
    }

    @Test
    void providerAlone() {
        assertEquals("playerpoints", CurrencySpec.parse("playerpoints").provider);
        assertEquals("playerpoints", CurrencySpec.parse("PP").provider);
        assertEquals("", CurrencySpec.parse("vault").currency);
        assertEquals("vault", CurrencySpec.parse("Vault").display);
    }

    @Test
    void unknownPrefixIsPartOfTheCurrency() {
        CurrencySpec s = CurrencySpec.parse("my:thing");
        assertNull(s.provider);
        assertEquals("my:thing", s.currency);
        assertNull(CurrencySpec.parse("").provider);
        assertNull(CurrencySpec.parse(null).provider);
    }
}
