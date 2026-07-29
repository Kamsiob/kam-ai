package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.model.ModelCatalog
import org.junit.Test

/**
 * Telling somebody their model is weak at the mode they just opened.
 *
 * Most of this file is about not saying it. The notice is worth having exactly
 * once, and everything that makes it appear more often than that turns an honest
 * limit into nagging, which is banned.
 */
class WeakModeNoteTest {

    private val weakAtLogic = ModelCatalog.basic.copy(weakModes = setOf(Mode.LOGIC))

    @Test
    fun aModelWithNoRecordedWeaknessSaysNothing() {
        // The default, and the common case. A caveat manufactured for every model
        // teaches people to skip all of them.
        Mode.entries.forEach { mode ->
            assertThat(WeakModeNote.shouldShow(mode, ModelCatalog.best, alreadySeen = false))
                .isFalse()
        }
    }

    @Test
    fun itIsSaidOnceForTheModeItAppliesTo() {
        assertThat(WeakModeNote.shouldShow(Mode.LOGIC, weakAtLogic, alreadySeen = false)).isTrue()
    }

    @Test
    fun itIsNeverSaidTwice() {
        assertThat(WeakModeNote.shouldShow(Mode.LOGIC, weakAtLogic, alreadySeen = true)).isFalse()
    }

    @Test
    fun otherModesOnTheSameModelSayNothing() {
        // The weakness is per mode. A model weak at argument is not therefore
        // weak at ordinary conversation, and saying so would be false.
        assertThat(WeakModeNote.shouldShow(Mode.GENERAL, weakAtLogic, alreadySeen = false))
            .isFalse()
        assertThat(WeakModeNote.shouldShow(Mode.BENCH, weakAtLogic, alreadySeen = false))
            .isFalse()
    }

    @Test
    fun noModelMeansNoNotice() {
        assertThat(WeakModeNote.shouldShow(Mode.LOGIC, null, alreadySeen = false)).isFalse()
    }

    @Test
    fun theWordingNamesTheModeTheModelAndTheWayOut() {
        val text = WeakModeNote.text(Mode.LOGIC, weakAtLogic)
        assertThat(text).contains("Logic Partner")
        assertThat(text).contains(weakAtLogic.displayName)
        // A path to act on, because this exists so somebody can choose well
        // rather than so we can say we told them.
        assertThat(text).contains("Model")
        // And it says the rest of the app is unaffected, so this reads as a
        // tradeoff rather than an apology for the whole application.
        assertThat(text).contains("The other modes are unaffected")
        // Plainly, not softened. A sound argument produced the fallback in three
        // runs out of three, so this mode does not work at this size rather than
        // working less well, and a user who reads a mild caveat and then watches
        // it fail entirely will trust the rest of the app's honesty less.
        assertThat(text).contains("needs a larger model")
    }
}
