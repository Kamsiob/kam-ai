package com.kamsiob.kamai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * No instrumentation test may reach the user's real data (#67).
 *
 * Two tests were found doing exactly that, both written at different times and
 * both passing while they did it. `BackupDbRoundTripTest` opened the real
 * database and called `deleteEverything()`. `PassphraseLayerTest` deleted the
 * wrapped database key and the Keystore entry that unwraps it, which is
 * unrecoverable: the conversations stay on disk as ciphertext nothing can read
 * again. `./gradlew connectedAndroidTest` on a phone with Kam AI installed would
 * have done both.
 *
 * Neither ever fired, because every instrumentation run in this project has
 * happened to be filtered to specific classes. That is luck twice over.
 *
 * The trap is that instrumentation runs **inside the app's own process**, so
 * `getApplicationContext()` is not a fixture. It is the user's install, with the
 * user's database, key material and files.
 *
 * This test is a JVM test on purpose. It reads the instrumentation sources as
 * text, so it runs in every ordinary `test` invocation and cannot itself be the
 * thing that needs a device.
 */
class InstrumentationSafetyTest {

    /**
     * Calls that reach real, shared state. A test wanting any of these must own
     * what it is touching (an in-memory database, a temp file) or refuse to run
     * where there is something to lose.
     */
    private val dangerous = listOf(
        "deleteEverything(",
        "DatabaseKey.destroy(",
        "KamRepository.get(",
        "KamDatabase.get(",
    )

    /**
     * Files allowed to mention them, each with the reason.
     *
     * Both entries are documentation rather than exemptions: they name the calls
     * inside comments explaining why the file no longer makes them, and a guard
     * that fires on its own explanation is a guard people delete.
     */
    private val allowed = mapOf(
        "BackupDbRoundTripTest.kt" to
            "builds its own in-memory database; the names appear in the comment saying why",
        "PassphraseLayerTest.kt" to
            "refuses to run when a real database exists; names the calls in that explanation",
        "WhisperTranscribeTest.kt" to
            "reads voiceDir() to find the installed model and writes nothing; checked by hand, " +
                "and read-only is the line this guard draws",
    )

    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    @Test
    fun noInstrumentationTestReachesTheRealDatabaseUnannounced() {
        val offenders = mutableListOf<String>()

        repoFile("app/src/androidTest/java/com/kamsiob/kamai").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                if (file.name in allowed) return@forEach
                val source = file.readText()
                dangerous.filter { source.contains(it) }.forEach { call ->
                    offenders += "${file.name} calls $call"
                }
            }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theTwoTestsThatWereFixedStayFixed() {
        // Named directly, because these are the two that actually did the damage
        // and a regression in either is the whole point of this file.
        val backup = repoFile(
            "app/src/androidTest/java/com/kamsiob/kamai/data/BackupDbRoundTripTest.kt",
        ).readText()
        assertThat(backup).contains("inMemoryDatabaseBuilder")

        val passphrase = repoFile(
            "app/src/androidTest/java/com/kamsiob/kamai/data/PassphraseLayerTest.kt",
        ).readText()
        assertThat(passphrase).contains("assumeFalse")
    }
}
