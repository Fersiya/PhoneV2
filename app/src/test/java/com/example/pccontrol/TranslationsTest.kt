package com.example.pccontrol

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationsTest {
    @Before fun on() { AppLang.english = true }
    @After fun off() { AppLang.english = false }

    @Test fun exactAndTemplate() {
        assertEquals("Cancel", L("Отмена"))
        assertEquals("Programs (5)", L("Программы (5)"))
        assertEquals("Phone: OnePlus PKG110, Android 17", L("Телефон: OnePlus PKG110, Android 17"))
        assertEquals("“Discord”: step 2/3", L("«Discord»: шаг 2/3"))
    }

    @Test fun longerTemplateWins() {
        assertEquals("Error 404: restart server.py on the PC", L("Ошибка 404: перезапустите server.py на ПК"))
        assertEquals("Error 500: boom", L("Ошибка 500: boom"))
        assertEquals("Error 7", L("Ошибка 7"))
    }

    @Test fun unknownStaysAsIs() {
        assertEquals("Что-то новое", L("Что-то новое"))
    }

    @Test fun russianModeIsIdentity() {
        AppLang.english = false
        assertEquals("Отмена", L("Отмена"))
    }

    @Test fun englishTextHasNoCyrillicAndKeepsPlaceholders() {
        val cy = Regex("[А-Яа-яЁё]")
        val ph = Regex("""\{\d+\}""")
        for ((k, v) in EN) {
            assertFalse("Кириллица в переводе: $v", cy.containsMatchIn(v))
            assertEquals("Плейсхолдеры не совпадают: $k", ph.findAll(k).map { it.value }.sorted().toList(), ph.findAll(v).map { it.value }.sorted().toList())
        }
        assertTrue(EN.size > 300)
    }
}
