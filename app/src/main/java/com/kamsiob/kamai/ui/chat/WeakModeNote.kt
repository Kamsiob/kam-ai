package com.kamsiob.kamai.ui.chat

import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.model.TierModel

/**
 * Said once when somebody opens a mode their installed model is weak at.
 *
 * Honest limits is a stated value of this application, and a model whose
 * weaknesses are known to us and not to the user contradicts it directly.
 * Somebody on an eight gigabyte phone who opens Logic Partner, watches it recite
 * its own instructions, and concludes the whole application is poor would be
 * right about what they saw and wrong about why, and only one of us was in a
 * position to prevent that.
 *
 * **The constraints on saying it are as important as saying it.** Once per mode
 * per model, ever. Never on a model that handles the mode well. Never a badge on
 * the mode chips, never a line above a reply, and nothing that keeps reminding
 * somebody their phone is small. A person on an eight gigabyte phone has one real
 * option and the tone owes them respect rather than an apology.
 *
 * It is also not legal cover. It exists so somebody can choose well, which is why
 * it names the path to the picker rather than merely disclaiming.
 */
object WeakModeNote {

    fun text(mode: Mode, model: TierModel): String {
        val what = when (mode) {
            Mode.LOGIC ->
                "Logic Partner asks more of the model than the other modes. Holding an " +
                    "argument means tracking what you claimed and what it rests on, across turns."
            Mode.BRAINSTORM ->
                "Brainstorm asks more of the model than the other modes. Running a session " +
                    "means choosing an approach and sticking to it while building on your answers."
            else ->
                "This mode asks more of the model than the others."
        }
        return "$what ${model.displayName} is one of the smaller models, and it does this " +
            "less reliably than the larger ones. Everything else works the same. If this " +
            "phone has room, there is a bigger one in Settings, then Model."
    }

    /**
     * Whether to say it at all.
     *
     * Nothing is said when the model handles the mode, and nothing is said twice.
     */
    fun shouldShow(mode: Mode, model: TierModel?, alreadySeen: Boolean): Boolean =
        model != null && !alreadySeen && mode in model.weakModes
}
