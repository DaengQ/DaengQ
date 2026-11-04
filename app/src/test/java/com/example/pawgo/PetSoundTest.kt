package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSoundTest {

    @Test
    fun mapsDogSounds() {
        assertEquals(PetBehavior.BARKING, PetSound.classify(listOf("Bark" to 0.8f)))
        assertEquals(PetBehavior.BARKING, PetSound.classify(listOf("Bow-wow" to 0.5f)))
        assertEquals(PetBehavior.WHINING, PetSound.classify(listOf("Whimper (dog)" to 0.4f)))
        assertEquals(PetBehavior.HOWLING, PetSound.classify(listOf("Howl" to 0.4f)))
        assertEquals(PetBehavior.GROWLING, PetSound.classify(listOf("Growling" to 0.6f)))
    }

    @Test
    fun picksHighestSpecificSoundOverGenericDog() {
        val scores = listOf("Dog" to 0.9f, "Bark" to 0.31f, "Whimper (dog)" to 0.6f)
        assertEquals(PetBehavior.WHINING, PetSound.classify(scores))
    }

    @Test
    fun genericDogIsNotAssumedToBeBarking() {
        assertEquals(PetBehavior.DOG_SOUND, PetSound.classify(listOf("Dog" to 0.5f, "Bark" to 0.1f)))
    }

    @Test
    fun whineClipScoresAreRecordedAsDogSound() {
        // 낑낑 시험 녹음을 모델에 직접 넣었을 때 나온 점수
        val scores = listOf("Dog" to 0.50f, "Growling" to 0.50f, "Whimper (dog)" to 0.08f)
        assertEquals(PetBehavior.DOG_SOUND, PetSound.classify(scores))
    }

    @Test
    fun growlingBelowItsThresholdIsIgnoredWithoutDog() {
        assertNull(PetSound.classify(listOf("Growling" to 0.59f)))
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
        assertEquals(PetSoundFilter.Decision(true, true), filter.onDetected(PetBehavior.BARKING, 0))
        assertEquals(PetSoundFilter.Decision(false, false), filter.onDetected(PetBehavior.BARKING, 1_000))
        val afterRecordCooldown = filter.onDetected(PetBehavior.BARKING, 30_000)
        assertTrue(afterRecordCooldown.record)
        assertFalse(afterRecordCooldown.alert)
        assertTrue(filter.onDetected(PetBehavior.BARKING, 60_000).alert)
        // 다른 소리는 따로 센다
        assertTrue(filter.onDetected(PetBehavior.WHINING, 60_001).record)
    }
}
