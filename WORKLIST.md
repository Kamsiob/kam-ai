# Worklist — owner bug-fix / refinement pass (2026-07-23)

Standalone task list from hands-on phone testing, layered on the standing directive
to finish the whole app. Each item: fix at the root, verify on the connected phone,
add a regression test, log decisions in DECISIONS.md. Standing rules apply (no em
dashes, two-spring motion, amber discipline, honesty voice). Items touching phases
not yet built are integrated into those phases rather than built early, noted here.

Status: [ ] todo · [~] in progress · [x] done & verified on device · [defer] moved to a later phase

## Items

- [x] 1. New chat opens the most recent chat instead of a new one (HIGH — blocks Logic testing).
      Root cause: view model keyed "chat-" (empty id sentinel) shared across all new chats,
      so the cached VM kept the previous conversation's id. Fixed with unique per-new-chat
      key via `conversationVmKey`. Unit test + device verified (open existing -> new chat empty;
      create chat -> new chat empty again; no stray conversations).
- [~] 2. Projects + bottom nav. DONE: nav is Projects/Chats/Follow-ups/Discover (New removed);
      create projects; per-project instructions (capped, re-injected, project-isolated); new chat in
      a project; move existing chats in/out via header picker (non-retroactive notice); main list
      excludes project chats; 'In <project>' indicator in the open chat; delete-project moves chats
      back to Chats. Device-verified (pirate project). REMAINING: multi-select bulk 'move to project'
      from the chat list; add-existing-from-inside-a-project; optional project notes field (migration).
      Original scope: Remove the bottom-nav "New" button to make room for BOTH Projects
      and Today (owner). FINAL nav order (owner): Today, Projects, Chats, Follow-ups, Discover (Chats centered).
      Build order now: Projects, Chats, Follow-ups, Discover; Today slots into position 1 when built. Build Projects (named container, persistent instructions + notes re-injected
      each turn, assign/reassign conversations like Claude, move semantics not retroactive, delete
      asks about conversations, cap instruction length). ISOLATION (owner): project instructions are
      EXCLUSIVE to that project and never leak into system-wide instructions or into chats outside
      the project; system-wide instructions (item 15) apply everywhere INCLUDING projects. Already
      enforced by buildPrompt (withProject only when conversation.projectId matches) + separate
      storage (ProjectEntity.instructions vs settings key); keep this in the Projects UI.
- [~] 3. Inference far slower than expected. DONE: native build confirmed optimized (Release/-O3/
      dotprod+i8mm+fp16/repack/mmap/flash-attn AUTO/n_batch 512 — not a debug-native issue);
      thread count fixed (perf cores capped at 4; measured E2B 6.9 -> 10.6 tok/s, +54%, on device;
      KamPerf logcat instrumentation added; debug.kamai.threads override). GPU offload unsupported
      (CPU correct). REMAINING: speculative decoding w/ Gemma-4 drafters (verify exist + llama.cpp
      support; drafter size in download/storage; measure or document unsupported); per-tier model
      selection (speed first, vision + mmproj projector size, document attachments, memory honesty);
      measure E4B tiers; if a tier too slow, pick faster model + record tradeoff. Update image copy
      if vision added.
- [x] 4. Swipe action buttons match row height. Rail is now drawn behind the row with
      matchParentSize and buttons fillMaxHeight, so they equal the row's exact height in every
      view (compact and cozy verified on device) with the row's corner radius.
- [~] 5. Global: nothing processes silently; anything slow is cancellable. DONE: chat thinking
      indicator now appears the instant a message is sent, through model load and prefill (was
      keyed on the empty answer bubble that only exists after load); streaming flips synchronously
      on send; testable `showThinkingIndicator` + unit test. REMAINING: quiz preparing state +
      leaving-screen behaviour; full app-wide audit of every slow op for immediate feedback + a
      real cancel path (model load, transcription, TTS, pack install, export/import, search).
- [x] 6. Read aloud cannot be stopped. Play now toggles to a Stop control while a response is being
      read and stops immediately when tapped; only one thing speaks at a time (starting a new read
      stops the current); a new message stops any read; nav-away already stops. `toggleSpeak` +
      `speakingMessageId` state. Device-verified play->stop->play. (Call/audio-focus interruption
      noted as a refinement; AudioTrack has no focus handling yet.)
- [x] 7. Incorrect microphone copy. Recording hint now reads "Listening. Tap stop when you are
      done." to match the Stop control shown (chat + Workbench, both audited). Device-verified.
- [ ] 8. Discover card animation (sweep out with tilt/fade, incoming rises via expressive spring;
      respect reduced motion). [may integrate with Discover phase]
- [ ] 9. Follow up from a Discover card directly (bookmark action).
- [ ] 10. Filter Follow-ups by source (chat, Logic, Discover); light, obvious, clear reset.
- [ ] 11. Discover discussion uses a scoped slide-up surface, not full chat window (title, source,
      distinct look, design system, no amber).
- [x] 12. Logic mode. DONE: persistent LogicBanner + "Logic Partner" tonal pill while active
      (design system, no amber); inline centered SYSTEM note at each switch point (both directions,
      stays in history) via new Role.SYSTEM (filtered from the model prompt and list snippet); mode
      now persisted to the conversation so a switch survives reopening. Verified on device: same
      model gives a helpful go-along Chat answer and a challenging "that is an assumption" Logic
      answer to the same kind of claim; context carried across switches. Tests: ModeSwitchTest
      (per-mode instructions differ, notice copy, SYSTEM filtered while turns carry forward).
- [ ] 13. Discover packs contain full articles, not just intros (preview stays intro; discussion
      draws on full text; pack grows to tens of MB; schema/manifest/version/pipeline/copy).
- [x] 14. Response formatting. DONE: dependency-free `MarkdownText` renderer styled from the design
      system (headings, bold/italic, inline code, fenced code blocks with mono + distinct bg +
      h-scroll, bullet/numbered lists, quotes, rule; tolerant of mid-stream unclosed markup), wired
      into assistant bubbles; formatting guidance added to shared HARD_RULES (match shape to content,
      do not over-format); empty streaming bubble suppressed. Tests: MarkdownParseTest,
      FormattingGuidanceTest (guidance present in every mode). Device-verified: short Q -> short
      answer, steps -> numbered list, code -> code block, no over-format. (Also reuse MarkdownText in
      the scoped Discover surface #11 and Workbench output when built.)
- [x] 15. System-wide custom instructions. DONE: Settings > Custom instructions screen (single
      field, 2000-char cap + counter), stored in settings table, re-injected every turn via
      SystemPrompts.withUserInstructions in buildPrompt at the right precedence: hard/mode rules
      (always win) > user instructions > project > memory. Test: InstructionPrecedenceTest.
      Device-verified: instruction "end every answer with PINEAPPLE" was followed.
- [~] 16. Memory system. DONE: retrieval is now relevance (keyword+recency, prefix-matched) within a
      context-fraction budget, not recency-only; injected near the front; auto-extraction runs as a
      batch over recent turns every few user messages (not every message), given existing facts to
      avoid repeats; dedup on a normalised form; parser strips leaked chat-template tokens (fixed junk
      "NONE</start_of_turn>" memories). Transparency/control already exist (Memory screen: see/edit/
      delete/bulk/all, auto-vs-manual). Tests: MemoryRetrievalTest. Device-verified end to end
      (remembered allergy -> later chat answered "Shellfish"). REMAINING (refs #16): full contradiction
      supersession (recency ranking currently favors the newer fact); optional "memory influenced this
      response" indicator.
- [x] 17. Conversation titles missing/poor by entry point. Extracted one shared `ConversationTitler`
      used by every path: ChatViewModel.respond() (in-app + Discover), the overlay handoff (was never
      titled -> the Eiffel bug), and title-on-open as a safety net for interrupted/legacy chats.
      Improved TITLE_INSTRUCTION (specific, no "title"/"conversation"); rejects generic output and
      falls back to an honest excerpt of the first question; stops after manual rename (titleIsManual);
      refresh at 8 messages. Efficiency: never loads the model just to title (uses the model when
      resident, else the instant excerpt fallback). Display fallback uses the snippet, not "New
      conversation". Tests: ConversationTitlerTest. Device-verified: interrupted "tell me about paris"
      now titled; new chats get model titles.
- [ ] 18. Power button assistant: quiet visual character; disable input while generating + stop
      control; fix greyed-out mic (diagnose real cause); Settings default input mode (voice/text,
      default text).
- [x] 19. Hidden fourth swipe action fixed. RAIL_WIDTH widened (175->232dp) to fit all four
      actions (Rename, Pin, Archive, Delete), each an equal share of the width; none hidden under
      the row at rest. Verified on device in two views.
- [x] 20. Open chat header + archived view. DONE: open conversation shows its title at the top with
      a polished treatment (accent bar marking it as the title + hairline separating the header zone
      from messages, per owner's follow-up) and an overflow menu with Rename/Archive/Delete (same
      confirmation tiers as the list; manual rename stops auto-titling via titleIsManual). Reactive
      title via ChatViewModel.title. Archived view (Pushed.Archived) reached from a quiet "Archived
      (N)" link on Chats shown only when some exist; open / Move to Chats (unarchive) / Delete;
      archiving reversible, deletion not. Archive/delete from header pop back to the list.
      Device-verified: header, menu, archive->link->archived view->unarchive round trip.
- [ ] 21. Discover scope boundary visible before hit: verify grounded vs open variants differ;
      clarify choice at point of selection; scope stated up front; out-of-scope offers one-tap
      "continue in open chat" carrying question+context. Audit for other invisible walls.
- [ ] 22. Capability transparency: declarative per-model capabilities in catalog; input bar adapts
      (hide unsupported controls on every surface); three states (can't/can't-afford/not-installed);
      filter pickers; honest model switching with existing attachments. Model picker shows
      capability icons (tappable explain), measured speed rating /5, quality rating /5 (relative),
      real numbers. Active model visible in chat. Keep claims consistent everywhere.

## Final steps (owner-directed, after the list AND the Today tab are done)

- [ ] FINAL. Update everything for release: GitHub repo (push), store listing descriptions, fresh
      in-app screenshots (real, over ADB), README, website copy, Q&A — all of it, kept consistent
      with the app's actual capabilities. Owner: "once this stuff is done including the Today tab, I
      need you to update github, descriptions, screenshots, readme, all of it." Then the APK on
      GitHub and the AAB on the computer are the very last steps (only when the owner says ready).

## Deferred (owner-directed)

- [defer] Today tab — full on-device newspaper. Spec in `docs/TODAY_SPEC.md`. Owner: "Build this
      only when directed; should not interrupt finishing and shipping the core application."
      When built, bottom nav grows to 5: Chats, Projects, Discover, Today, Saved.

## Notes / follow-ups discovered

- Conversation view models are Activity-scoped and not cleared on back-pop, so each opened or new
  chat leaks a lightweight ChatViewModel for the session. Correctness is fine after item 1. A
  proper fix is per-back-stack-entry ViewModelStoreOwner (nav-compose style). Low priority.
