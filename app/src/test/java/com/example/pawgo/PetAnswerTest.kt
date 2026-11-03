package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class PetAnswerTest {

    private fun behavior(text: String) = (PetAnswer.parse(text) as? PetAnswer.Behavior)?.behavior

    @Test
    fun recognizesBehaviors() {
        assertEquals(PetBehavior.BARKING, behavior("계속 짖어요"))
        assertEquals(PetBehavior.WHINING, behavior("좀 낑낑거려요"))
        assertEquals(PetBehavior.CARSICK, behavior("멀미하는 것 같아"))
        assertEquals(PetBehavior.SLEEPING, behavior("자고 있어요"))
        assertEquals(PetBehavior.TOILET, behavior("화장실 가고 싶어 해"))
        assertEquals(PetBehavior.EXCITED, behavior("엄청 신났어"))
    }

    @Test
    fun ignoresSpacesInRecognizedText() {
        assertEquals(PetBehavior.WHINING, behavior("낑 낑 거려요"))
    }

    @Test
    fun recognizesFineAndUnknown() {
        assertEquals(PetAnswer.Fine, PetAnswer.parse("괜찮아요"))
        assertEquals(PetAnswer.Fine, PetAnswer.parse("얌전히 있어"))
        assertEquals(PetAnswer.Unknown, PetAnswer.parse("음"))
    }
}
