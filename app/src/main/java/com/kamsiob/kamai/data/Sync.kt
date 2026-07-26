package com.kamsiob.kamai.data

/**
 * What the data model has to get right *before* sync exists, and nothing more.
 *
 * No sync is built here. There is no transport, no server, no scheduler and no
 * network code anywhere in this file. What is here is the set of decisions that
 * are cheap now and expensive later, because they are the ones that need a
 * migration over other people's data if they are made wrong.
 *
 * The four that matter:
 *
 * 1. **Identity.** Already right. Every row is keyed on a random UUID or on a
 *    natural key that comes from a pack file rather than a device, so two phones
 *    working offline cannot collide.
 * 2. **Deletion has to leave a mark.** Without one, "this was deleted" and "this
 *    phone has not seen it yet" are the same state, and the row comes back.
 * 3. **Ordering cannot use the wall clock.** Clocks go backwards.
 * 4. **The conflict rule has to be identical on every device**, or two phones
 *    reconcile the same pair of writes differently and never converge.
 */

/**
 * A logical clock, which is the ordering primitive the rest of this depends on.
 *
 * Every write stamps the row with the next value. Reading "everything since N" is
 * then one indexed comparison, and the order of local writes is total and never
 * moves.
 *
 * **Why not `updatedAt`.** It is already there and it is nearly free, which makes
 * it tempting, and it is wrong. `System.currentTimeMillis()` is not monotonic: an
 * NTP correction, a user setting the clock, a timezone change or a DST boundary
 * can all move it backwards. Order writes by it and an edit gets discarded as
 * stale because the phone's clock stepped back between two keystrokes. It stays
 * in the schema, because "when did I write this" is a real thing to show a
 * person, and it stops being load-bearing for correctness.
 *
 * **It is a Lamport clock, not a counter.** On receiving a peer's row the local
 * value moves to `max(local, theirs) + 1`. That single line is what makes the
 * ordering causal rather than merely local: if this device saw a write before
 * making its own, every device agrees its own write came second. A plain
 * per-device counter cannot express that, and two devices at 5 and 5000 would
 * have the second win every argument forever regardless of what happened.
 *
 * Pure and in-memory. Persisting the value is the caller's job, and where it goes
 * is [SyncKeys.LAMPORT].
 */
class LamportClock(initial: Long = 0L) {

    var value: Long = initial
        private set

    /** The stamp for a local write. */
    fun tick(): Long {
        value += 1
        return value
    }

    /**
     * Fold in a stamp seen from somewhere else.
     *
     * Called for every incoming row, including ones that lose their conflict:
     * having *seen* a write is what has to be recorded, and whether it won is a
     * separate question.
     */
    fun observe(seen: Long) {
        if (seen > value) value = seen
    }
}

/**
 * Where a row was last written, and when in logical time.
 *
 * Both halves are needed. The stamp orders writes; the device id breaks ties. A
 * tie is not exotic: two phones editing the same project name while both offline
 * come back with the same stamp routinely, because neither saw the other and both
 * were at the same count. Without a tiebreak each device prefers its own copy,
 * both think they are settled, and they never converge.
 *
 * The tiebreak is a string comparison of the device id. It is arbitrary, which is
 * the point: it is arbitrary *in the same direction on every device*.
 */
data class Stamp(val lamport: Long, val deviceId: String) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int {
        val byClock = lamport.compareTo(other.lamport)
        return if (byClock != 0) byClock else deviceId.compareTo(other.deviceId)
    }
}

/**
 * Which of two versions of the same row survives.
 *
 * Last writer wins, ordered by [Stamp], with one exception: a deletion beats an
 * edit at the same stamp.
 *
 * **Why last writer wins, given it loses data.** The alternative is merging, and
 * merging needs to know what a field means. For the things here it either cannot
 * help or is not wanted: a message is immutable once it has finished streaming,
 * so there is nothing to merge; a project's instructions are prose, and a
 * three-way merge of two people's prose produces text neither of them wrote,
 * which is worse than losing the older edit. Where merging genuinely is right --
 * counters -- the answer is not to sync them, see [SyncPolicy].
 *
 * **Why deletion wins the tie.** The two failures are not equal. Losing an edit
 * costs someone a retype and they can see it happened. A deleted conversation
 * reappearing is the app overriding a decision the person made on purpose, they
 * may not notice, and if they deleted it because it was sensitive then the app
 * has just undone that. So the asymmetry is deliberate and it is the safer way to
 * be wrong.
 */
object Reconcile {

    enum class Winner {
        LOCAL,
        REMOTE,

        /**
         * The two sides are the same write, so there is nothing to choose.
         *
         * This case earns its own name rather than defaulting to LOCAL, and the
         * reason is worth recording because a property test is what found it.
         * Answering LOCAL here is harmless in its effect and wrong as an answer:
         * each phone would report that its own copy won, so the function was not
         * symmetric, and a rule that gives two devices different answers is
         * exactly the class of bug that stops a sync ever converging. Here the
         * asymmetry could not bite, since an identical stamp means the identical
         * write and therefore identical content. Somewhere else it would.
         *
         * It is also the useful answer: a caller that gets SAME can skip the
         * write instead of performing one that changes nothing.
         */
        SAME,
    }

    /**
     * @param localDeleted whether the local side holds a tombstone for this row.
     * @param remoteDeleted whether the incoming side does.
     */
    fun winner(
        local: Stamp,
        remote: Stamp,
        localDeleted: Boolean = false,
        remoteDeleted: Boolean = false,
    ): Winner {
        val c = local.compareTo(remote)
        if (c != 0) return if (c > 0) Winner.LOCAL else Winner.REMOTE
        // Same stamp and same device id is the same write arriving twice, which is
        // ordinary in anything that retries. A stamp is only ever handed out once
        // per write, so this really is one write and not two that collided.
        if (localDeleted == remoteDeleted) return Winner.SAME
        return if (localDeleted) Winner.LOCAL else Winner.REMOTE
    }
}

/**
 * What leaves the device, and what has no business doing so.
 *
 * Written down per table because the default has to be a decision rather than an
 * accident. Getting this wrong in the permissive direction means a phone telling
 * another phone facts about a disk it cannot see.
 */
object SyncPolicy {

    enum class Scope {
        /** The user's own content. Follows them to every device. */
        SYNCED,

        /** True of this phone only. Sending it would be a lie on the other one. */
        DEVICE_LOCAL,
    }

    /**
     * The tables that carry a [Stamp] and a tombstone, and the reasoning for the
     * ones that do not.
     *
     * - `projects`, `conversations`, `messages`, `memory_entries`, `follow_ups`
     *   are the user's content. The whole point.
     * - `discover_drawn` syncs. It is which moments have already been dealt, and
     *   a second phone that has not been told will deal them again, which reads
     *   as the app having forgotten.
     * - `discover_quiz_stats` does not, and this is the interesting one. It is
     *   three counters. Last writer wins on a counter *loses counts*: two devices
     *   at 10 and 12 settle on 12 and the ten are gone. Doing it properly means
     *   per-device counters summed on read, which is a real change to the primary
     *   key and to every read, in exchange for a quiet tally with deliberately no
     *   streaks or goals attached to it. Not worth it. Device-local, and honest.
     * - `artifacts` never syncs. It is a description of what is on this phone's
     *   storage: file names, sizes, hashes, which model is active. Every value is
     *   false on another device.
     * - `settings` is split per key, see [SYNCED_SETTINGS].
     */
    val TABLES: Map<String, Scope> = mapOf(
        "projects" to Scope.SYNCED,
        "conversations" to Scope.SYNCED,
        "messages" to Scope.SYNCED,
        "memory_entries" to Scope.SYNCED,
        "follow_ups" to Scope.SYNCED,
        "discover_drawn" to Scope.SYNCED,
        "discover_quiz_stats" to Scope.DEVICE_LOCAL,
        "artifacts" to Scope.DEVICE_LOCAL,
        "settings" to Scope.SYNCED,
    )

    /**
     * The setting keys that follow the user. Everything else stays put.
     *
     * An allowlist, not a denylist, and not a naming convention. A key added later
     * and not thought about should fail to sync, which is recoverable, rather than
     * sync when it describes hardware, which is not. Guessing from the key name is
     * exactly how "the active model" ends up on a phone that does not have it.
     */
    val SYNCED_SETTINGS: Set<String> = setOf(
        "theme",
        "accent",
        "confirmChatDelete",
        "autoArchive",
        "customInstructions",
        "assistantDefaultVoice",
    )

    fun syncs(table: String): Boolean = TABLES[table] == Scope.SYNCED

    fun syncsSetting(key: String): Boolean = key in SYNCED_SETTINGS
}

/**
 * What two devices have to agree on before either sends anything.
 *
 * Separate from the Room version and from the backup format on purpose. They
 * answer different questions: Room's version is what this phone's file looks like
 * on disk, and it moves for reasons a peer does not care about, such as adding an
 * index. This is the shape of what goes over the wire.
 */
object SyncWire {

    /**
     * Incremented when the payload shape changes in a way an older build cannot
     * read correctly.
     */
    const val VERSION = 1

    sealed interface Verdict {
        data object Compatible : Verdict

        /** The other side is newer. This build must not touch its data. */
        data class NeedsUpdate(val message: String) : Verdict

        /** The other side is older, so it is the one that has to update. */
        data class PeerNeedsUpdate(val message: String) : Verdict
    }

    /**
     * Whether this build may apply a payload written by [peerVersion].
     *
     * **A newer payload is refused whole, rather than applied partially.** The
     * tempting alternative is to take the fields you recognize and skip the rest,
     * which quietly destroys the others: the old build reads a row, ignores the
     * field it does not know, writes the row back with a fresh stamp, and that
     * write now wins everywhere. The data is gone, no error was raised, and the
     * device that had it correct is the one that gets overwritten. Refusing is
     * visible, and a visible stop is repairable in a way that silent loss is not.
     *
     * The wording says which phone needs attention, because "sync failed" sends
     * someone to look at the wrong one.
     */
    fun check(peerVersion: Int): Verdict = when {
        peerVersion == VERSION -> Verdict.Compatible
        peerVersion > VERSION -> Verdict.NeedsUpdate(
            "Your other device is running a newer version of Kam AI. Update this " +
                "one and they will sync. Nothing has been changed on either.",
        )
        else -> Verdict.PeerNeedsUpdate(
            "Your other device is running an older version of Kam AI. Update it " +
                "there and they will sync. Nothing has been changed on either.",
        )
    }
}

/** The setting keys this file owns. Kept together so they are not retyped. */
object SyncKeys {

    /**
     * This install's identity, for the [Stamp] tiebreak.
     *
     * A random UUID made once and kept. Deliberately not the Android id, the
     * advertising id, the serial or anything else derived from hardware: those
     * identify a person across apps, this only needs to tell two copies of one
     * app apart, and a value that survives an uninstall would be a tracking
     * identifier with no purpose.
     */
    const val DEVICE_ID = "sync.deviceId"

    /** The persisted [LamportClock] value. */
    const val LAMPORT = "sync.lamport"
}
