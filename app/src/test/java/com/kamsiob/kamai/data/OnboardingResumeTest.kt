package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where onboarding starts, after leaving it (#117).
 *
 * Back from onboarding leaves the app rather than moving within it, so the
 * position has to survive the process going away entirely. That makes this a
 * question about what is on disk, not about what a screen remembers, which is
 * why it is tested here rather than in a UI test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingResumeTest {

    private lateinit var db: KamDatabase
    private lateinit var repository: KamRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KamRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a first run starts at the beginning`() = runTest {
        assertThat(repository.onboardingSlide()).isEqualTo(0)
    }

    @Test
    fun `the slide survives, because the process may not`() = runTest {
        repository.setOnboardingSlide(4)
        assertThat(repository.onboardingSlide()).isEqualTo(4)
    }

    @Test
    fun `replaying onboarding starts it over rather than resuming`() = runTest {
        // The case this exists for: somebody reaches the model slide on first
        // run, finishes, and later opens "What Kam AI is for" from Settings. A
        // resume point left over from the first run would drop them into the
        // middle of an explanation they asked to see from the start.
        repository.setOnboardingSlide(4)
        repository.markOnboardingDone()
        assertThat(repository.isOnboardingDone()).isTrue()

        repository.replayOnboarding()

        assertThat(repository.isOnboardingDone()).isFalse()
        assertThat(repository.onboardingSlide()).isEqualTo(0)
    }

    @Test
    fun `replaying the introduction offers the mode explanation again`() {
        // "Shown once, ever" means not shown twice by accident. Somebody opening
        // "What Kam AI is for" is asking to be re-introduced, and the mode
        // control explanation is part of that introduction (#93).
        kotlinx.coroutines.test.runTest {
            repository.markModeHintSeen()
            assertThat(repository.modeHintSeen()).isTrue()

            repository.replayOnboarding()

            assertThat(repository.modeHintSeen()).isFalse()
        }
    }

    @Test
    fun `a value that is not a number is treated as the beginning`() = runTest {
        // Nothing writes a non-number today. This is about what happens when a
        // restore, a migration or a future change puts something unexpected in
        // the row: starting at the beginning is recoverable, throwing on the way
        // to the first frame is not.
        repository.putSetting(KamRepository.Keys.ONBOARDING_SLIDE, "halfway")
        assertThat(repository.onboardingSlide()).isEqualTo(0)
    }
}
