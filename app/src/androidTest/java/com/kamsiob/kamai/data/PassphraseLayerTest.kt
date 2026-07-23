package com.kamsiob.kamai.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The separate-passphrase layer over the database key. PART 3.
 *
 * This is what makes the strong lock actually strong: the database key cannot be
 * unwrapped without the passphrase, so a forgotten one is genuinely
 * unrecoverable rather than merely inconvenient.
 */
@RunWith(AndroidJUnit4::class)
class PassphraseLayerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keyFile = File(context.filesDir, "kamai_db.key")

    @Before @After
    fun clean() {
        keyFile.delete()
        DatabaseKey.destroy(context)
    }

    @Test
    fun aKeyWithNoPassphraseOpensWithoutOne() {
        val key = DatabaseKey.getOrCreate(context, userSecret = null)
        assertThat(key).hasLength(32)
        assertThat(DatabaseKey.hasUserLayer(context)).isFalse()
        // Same bytes back on a second call.
        assertThat(DatabaseKey.getOrCreate(context, null)).isEqualTo(key)
    }

    @Test
    fun addingAPassphraseLayerKeepsTheSameKeyButRequiresTheSecret() {
        val key = DatabaseKey.getOrCreate(context, null)

        DatabaseKey.rewrap(context, currentSecret = null, newSecret = "correct horse".toCharArray())
        assertThat(DatabaseKey.hasUserLayer(context)).isTrue()

        // The underlying database key is unchanged, so the database still opens.
        val withSecret = DatabaseKey.getOrCreate(context, "correct horse".toCharArray())
        assertThat(withSecret).isEqualTo(key)
    }

    @Test
    fun theWrongPassphraseCannotUnwrapTheKey() {
        DatabaseKey.getOrCreate(context, null)
        DatabaseKey.rewrap(context, null, "the real one".toCharArray())

        val failed = runCatching {
            DatabaseKey.getOrCreate(context, "a guess".toCharArray())
        }.isFailure
        assertThat(failed).isTrue()
    }

    @Test
    fun aPassphraseLockedKeyCannotBeOpenedWithNoPassphraseAtAll() {
        DatabaseKey.getOrCreate(context, null)
        DatabaseKey.rewrap(context, null, "guarded".toCharArray())

        val failed = runCatching {
            DatabaseKey.getOrCreate(context, userSecret = null)
        }.isFailure
        assertThat(failed).isTrue()
    }

    @Test
    fun removingThePassphraseLayerReturnsToOpeningWithoutOne() {
        val key = DatabaseKey.getOrCreate(context, null)
        DatabaseKey.rewrap(context, null, "temporary".toCharArray())
        DatabaseKey.rewrap(context, "temporary".toCharArray(), newSecret = null)

        assertThat(DatabaseKey.hasUserLayer(context)).isFalse()
        assertThat(DatabaseKey.getOrCreate(context, null)).isEqualTo(key)
    }

    @Test
    fun destroyMakesTheKeyPermanentlyGone() {
        val key = DatabaseKey.getOrCreate(context, null)
        DatabaseKey.destroy(context)
        assertThat(DatabaseKey.exists(context)).isFalse()
        // A fresh key is a different key: the old data is unrecoverable.
        val fresh = DatabaseKey.getOrCreate(context, null)
        assertThat(fresh).isNotEqualTo(key)
    }
}
