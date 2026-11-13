package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanTest {

    @Test
    fun picksParticleByLastSyllable() {
        assertEquals("콩이는", "콩이".withEunNeun())
        assertEquals("보름은", "보름".withEunNeun())
        assertEquals("콩이가", "콩이".withIGa())
        assertEquals("보름이", "보름".withIGa())
        assertEquals("Max는", "Max".withEunNeun())
    }

    @Test
    fun addsCallingIOnlyAfterBatchim() {
        assertEquals("봄이는", "봄".withCallingI().withEunNeun())
        assertEquals("봄이가", "봄".withCallingI().withIGa())
        assertEquals("콩이는", "콩이".withCallingI().withEunNeun())
        assertEquals("Max는", "Max".withCallingI().withEunNeun())
    }
}
