package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSoundTest {

    @Test
    fun mapsDogSoundsToThreeTypes() {
        assertEquals(PetBehavior.WHINING, PetSound.classify(listOf("Whimper (dog)" to 0.4f)))
        assertEquals(PetBehavior.HOWLING, PetSound.classify(listOf("Howl" to 0.4f)))
        assertEquals(PetBehavior.CRYING, PetSound.classify(listOf("Bark" to 0.8f)))
        assertEquals(PetBehavior.CRYING, PetSound.classify(listOf("Yip" to 0.5f)))
        assertEquals(PetBehavior.CRYING, PetSound.classify(listOf("Bow-wow" to 0.5f)))
    }

    @Test
    fun picksHighestSpecificSoundOverGenericDog() {
        val scores = listOf("Dog" to 0.9f, "Bark" to 0.31f, "Whimper (dog)" to 0.6f)
        assertEquals(PetBehavior.WHINING, PetSound.classify(scores))
    }

    @Test
    fun genericDogIsRecordedAsCrying() {
        assertEquals(PetBehavior.CRYING, PetSound.classify(listOf("Dog" to 0.5f, "Bark" to 0.1f)))
    }

    @Test
    fun growlingIsNotATypeAnymore() {
        assertNull(PetSound.classify(listOf("Growling" to 0.9f)))
    }

    @Test
    fun ignoresLowScoresAndOtherSounds() {
        assertNull(PetSound.classify(listOf("Bark" to 0.29f, "Dog" to 0.29f)))
        assertNull(PetSound.classify(listOf("Speech" to 0.9f, "Animal" to 0.9f)))
        assertNull(PetSound.classify(emptyList()))
    }

    @Test
    fun filterLimitsRecordsAndAlertsPerSound() {
        val filter = PetSoundFilter(recordCooldownMs = 30_000, alertCooldownMs = 60_000)
        assertEquals(PetSoundFilter.Decision(true, true), filter.onDetected(PetBehavior.CRYING, 0))
        assertEquals(PetSoundFilter.Decision(false, false), filter.onDetected(PetBehavior.CRYING, 1_000))
        val afterRecordCooldown = filter.onDetected(PetBehavior.CRYING, 30_000)
        assertTrue(afterRecordCooldown.record)
        assertFalse(afterRecordCooldown.alert)
        assertTrue(filter.onDetected(PetBehavior.CRYING, 60_000).alert)
        // 다른 소리는 따로 센다
        assertTrue(filter.onDetected(PetBehavior.WHINING, 60_001).record)
    }

    @Test
    fun readsOldStoredTypes() {
        assertEquals(PetBehavior.CRYING, PetBehavior.fromStoredName("BARKING"))
        assertEquals(PetBehavior.CRYING, PetBehavior.fromStoredName("DOG_SOUND"))
        assertEquals(PetBehavior.WHINING, PetBehavior.fromStoredName("WHINING"))
        assertNull(PetBehavior.fromStoredName("SLEEPING"))
    }
}
