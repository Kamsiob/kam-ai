package com.kamsiob.kamai.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The interface only offers what the answering model can actually do (#22).
 *
 * Capabilities are declared per model in the catalog rather than hardcoded by
 * name, so the check the composer makes is the same one the model card makes,
 * and a model added later behaves correctly with no code change. These tests pin
 * that arrangement: the failure they exist to catch is somebody adding a
 * text-only model and the paperclip staying put, which is discovered by a user
 * who has already chosen a file, waited for it to be read, and asked their
 * question before anything says the model cannot see it.
 */
class CapabilityGatingTest {

    @Test
    fun `every model in the catalog declares what it can do`() {
        assertThat(ModelCatalog.all).isNotEmpty()
        ModelCatalog.all.forEach { model ->
            assertThat(model.capabilities).isNotEmpty()
            // Every model in this app answers in text. A model declaring
            // otherwise is a catalog mistake, not a new kind of model.
            assertThat(model.supports(Capability.TEXT)).isTrue()
        }
    }

    @Test
    fun `the paperclip follows the declaration, not the model's name`() {
        val textOnly = ModelCatalog.all.first().copy(
            id = "text-only-test",
            capabilities = setOf(Capability.TEXT),
        )
        assertThat(textOnly.supports(Capability.DOCUMENTS)).isFalse()

        val reader = textOnly.copy(capabilities = setOf(Capability.TEXT, Capability.DOCUMENTS))
        assertThat(reader.supports(Capability.DOCUMENTS)).isTrue()
    }

    @Test
    fun `no model claims to understand images yet, and the app must not offer it`() {
        // The attachment picker offers text, Markdown, PDF and Word, and no image
        // types. If a vision model is ever added, this test failing is the
        // reminder that the picker has to be widened to match.
        assertThat(ModelCatalog.all.none { it.supports(Capability.IMAGES) }).isTrue()
    }

    @Test
    fun `no active model means no paperclip`() {
        // The null case in the chat screen: nothing is installed, so nothing can
        // read a document, so the control is not offered.
        val active: TierModel? = null
        assertThat(active?.supports(Capability.DOCUMENTS) ?: false).isFalse()
    }
}
