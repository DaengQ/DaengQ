package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Test

class PetCommandTest {

    @Test
    fun prdExamples() {
        assertEquals(PetCommand.Change(null, PetBehavior.CRYING), PetCommand.parse("댕큐, 아까 거 울음으로 바꿔줘"))
        assertEquals(PetCommand.Change(PetBehavior.HOWLING, PetBehavior.CRYING), PetCommand.parse("하울링 아니고 울음이야"))
        assertEquals(PetCommand.Delete(null), PetCommand.parse("방금 거 지워줘"))
        assertEquals(PetCommand.Delete(PetBehavior.HOWLING), PetCommand.parse("하울링 기록 삭제해 줘"))
        assertEquals(PetCommand.Add(PetBehavior.WHINING), PetCommand.parse("지금 낑낑거려"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("됐어"))
    }

    @Test
    fun changesBetweenTwoNamedTypes() {
        assertEquals(
            PetCommand.Change(PetBehavior.WHINING, PetBehavior.HOWLING),
            PetCommand.parse("낑낑거림을 하울링으로 바꿔")
        )
    }

    @Test
    fun howlingIsNotReadAsCrying() {
        assertEquals(PetCommand.Add(PetBehavior.HOWLING), PetCommand.parse("지금 하울링해"))
    }

    @Test
    fun ignoresSpacesAndUnclearSpeech() {
        assertEquals(PetCommand.Add(PetBehavior.WHINING), PetCommand.parse("낑 낑 거려"))
        assertEquals(PetCommand.Unknown, PetCommand.parse("음"))
        assertEquals(PetCommand.Unknown, PetCommand.parse("울음 아니야"))
    }

    @Test
    fun doesNotDoWhatTheDriverSaidNotToDo() {
        assertEquals(PetCommand.Change(null, PetBehavior.CRYING), PetCommand.parse("지우지 말고 울음으로 바꿔"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("지우지 마"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("아니야 지우지 마"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("삭제하지 마"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("지우면 안 돼"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("하울링 바꾸지 마"))
        assertEquals(PetCommand.Cancel, PetCommand.parse("울음 맞아 바꾸지 마"))
    }

    @Test
    fun saidItDidNotMakeTheSoundDeletesThatRecord() {
        assertEquals(PetCommand.Delete(PetBehavior.CRYING), PetCommand.parse("방금 안 울었어"))
        assertEquals(PetCommand.Delete(PetBehavior.WHINING), PetCommand.parse("낑낑 안 했어"))
        assertEquals(PetCommand.Delete(PetBehavior.CRYING), PetCommand.parse("울지 않았어"))
        assertEquals(PetCommand.Unknown, PetCommand.parse("안 했어"))
    }

    @Test
    fun understandsOtherWaysOfSayingDelete() {
        assertEquals(PetCommand.Delete(PetBehavior.WHINING), PetCommand.parse("낑낑 지울래"))
        assertEquals(PetCommand.Delete(null), PetCommand.parse("방금 거 지운다"))
        assertEquals(PetCommand.Delete(PetBehavior.HOWLING), PetCommand.parse("하울링 지울래"))
        assertEquals(PetCommand.Delete(PetBehavior.WHINING), PetCommand.parse("낑낑 없앨래"))
    }
}
