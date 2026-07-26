# DESIGN.md · Kam AI by Kamsiob

This document is the single source of truth for how Kam AI looks, moves, and speaks. Every screen, component, and line of copy must be checked against it. When code and this document disagree, this document wins. It must be committed to the repository and kept updated as decisions change; it describes the app as it now is and as it is intended to be, with anything not yet built marked as such.

This document has been reconciled with the built app. It is read together with MASTER_SPEC.md, DECISIONS.md, and the open GitHub issues, which together are the current source of truth; anything in older prompts or earlier conversation that conflicts with them is superseded. Where a behavior here is still pending rather than built, it says so and the open issue is the record of what remains.

## 1. What this app is

Kam AI is a fully local AI app for Android. The AI model is downloaded onto the phone and runs there. Conversations, memory, projects, and everything else stay on the device. The app is free, open source under AGPLv3, has no accounts, no ads, no subscriptions, and collects zero data.

Positioning, to be carried into every store listing, README, and description: Kam AI is a private thinking and drafting tool for things that should not leave the device. It is good at transforming, organizing, and rephrasing text the user gives it, at everyday questions, and at pushing back on ideas. It is explicitly NOT positioned as a private ChatGPT and must never imply it matches large cloud model capability. Small on-device models know less, get some facts wrong, cannot make images, and are weaker at long polished documents. The app says this plainly and builds features around it (the bookmark and the Follow-ups list) instead of hiding it.

Hard identity rules, non-negotiable in every mode and every piece of copy: no characters, no roleplay, no pretend companion, no emotional manipulation, no hyping, no getting personal, no sycophancy. These are stated design commitments, visible to users, not just internal rules.

## 2. The mark

The Kam AI mark is a lit core held inside a broken ring, with a smaller inner arc as a counterweight.

Geometry, on a 48 by 48 viewbox: outer ring at radius 16, stroke width 3.4, rounded caps, drawn as a 270 degree arc so a 90 degree gap sits at the top right. Inner accent arc at radius 10.5, stroke 2.2, about 55 degrees long, rotated 150 degrees, at 55 percent opacity. Core is a circle at radius 6.6 filled with a radial gradient from a pale mint highlight at the upper left (#C9F5DB) through the mid green (#4FBF85) to a deep green edge (#1F6B44).

Meaning, usable in docs and marketing: the ring is enclosure, your data held on the device. The gap is the one deliberate opening, the things the user chooses to let out (downloads, opt-in search). The core is the on-device light, the indicator that the machine doing the thinking is this one.

Rules: the ring and inner arc take the current theme accent color. The core gradient never changes. The mark may breathe (slow scale to 1.07 with a soft green drop shadow over a 4.2 second ease-in-out loop) when used as a status indicator. It appears small and quiet at the top of every screen next to the wordmark, never as a large blocking element outside of onboarding and About.

App icon: the same geometry as an Android adaptive icon. Foreground is the mark, background is the theme ivory (light) with the deep pine variant used where a dark background is required. The Android 13+ themed monochrome icon variant is the ring, inner arc, and a solid core dot in single color, which this geometry supports natively. No thin details that die at 48 pixels.

## 3. Color

The accent is user-chosen from sixteen colors (eight bright, eight earthy, green the default), each with separately tuned light and dark shades and its own on-accent text color, all contrast-checked in both themes. A reserved gold (a brighter, more saturated color than the former amber, moved further from Workbench's mustard so the two never read as the same thing: gold is luminous, mustard is deep and dull) is a separate reserved color the accent never touches. Gold is reserved for four things only: saved items (the bookmark on a response, on a Discover card, and the Follow-ups list they feed, all filling the same gold when set), locked model tiers, the Support this work button, and destructive-action labels (for example Delete). Gold must never appear anywhere else. No purple anywhere. No gradients except the core of the mark. No sparkles, no glow effects beyond the mark's breathing shadow and the onboarding ripple rings.

The four modes each have one identity hue, an identity signal only, never used for general UI state (buttons, links, selection, focus stay on the accent), and always paired with the mode name or icon so color is never the only carrier of meaning. Light theme: General 2E7A52, Logic 2F5D8C, Brainstorm 9A3B33 (a deep maroon red, not pink), Workbench B0851C (a deep mustard). Dark theme: General 6FD19E, Logic 7FB3E0, Brainstorm E2705F, Workbench C9A44E. Discover, which is a source rather than one of the four modes, carries its own identity (light 6A4A9C, dark B79CE6) so its conversations are legible in the chat list without being one of the mode filters.

Light theme (warm ivory):
- Background: #F6F4EC
- Surface (cards): #FFFFFF
- Secondary surface: #EDEDE2
- Tonal fill (green tint): #E2EDE0, with tonal text #2A5C42
- Text primary: #1B241E
- Text secondary: #61705F
- Text tertiary: #95A093
- Accent (default green): #2E7A52, with on-accent text #F2FBF4. Fifteen other accents ship alongside, each with its own tuned light shade and on-accent text.
- Reserved gold: #EFA913 for fills, icons, dots, and the support button; #8A5F0D wherever it is text or a glyph on the ivory ground, because the bright gold is too light to read there (the spec's #96690F measured 4.41 on ivory, just under AA, so it was nudged to #8A5F0D at 5.12); soft gold fill #FCEFC6 for tinted backgrounds.
- Borders: rgba(27,36,30,0.08)
- Card shadow: 0 10px 28px rgba(38,64,48,0.10) plus 0 2px 6px rgba(38,64,48,0.06)

Dark theme (deep pine):
- Background: #0F1512
- Surface: #182019
- Secondary surface: #131A15
- Tonal fill: #1D2E23, with tonal text #9FDDBA
- Text primary: #EDF2EA
- Text secondary: #9AA69B
- Text tertiary: #5E6A60
- Accent (default green): #6FD19E, with on-accent text #0A1B11. The other fifteen accents each carry a tuned dark shade.
- Reserved gold: #FFD166 for fills, icons, dots, text, and the support button (one luminous gold works for all on pine); soft gold fill #332812 for tinted backgrounds.
- Borders: rgba(237,242,234,0.07)
- Card shadow: 0 5px 16px rgba(0,0,0,0.18). Dark shadows must stay this soft. Heavy black shadows in dark mode read as dirty translucent boxes and were explicitly rejected.

Backgrounds are never pure black or pure white. Theme switching crossfades all surfaces together over the slow duration; nothing snaps.

## 4. Typography

- Display face: Sora, weights 600 and 700, for screen titles, card titles, tier names, mode names, the wordmark. Letter spacing slightly negative (about -0.02em to -0.03em) at large sizes.
- Body face: Manrope, weights 400 to 800, for everything else.
- Utility mono: JetBrains Mono, for specs and metadata only: RAM figures, download sizes, versions, timestamps, source chips, section eyebrow labels. Mono signals a fact about the machine.
- Never use Inter, Roboto, or Open Sans as display faces. Body text at readable sizes, roughly 14 to 16sp equivalents in the real app; metadata smaller but never below accessible minimums. Weight carries hierarchy, not extra families.
- The italic serif for the Brainstorm empty-state nudge. The other three faces (Sora, Manrope, JetBrains Mono) are weight-axis variable fonts with no italic, so without this the only italic in the app would be the synthetic slant markdown emphasis produces. It needed an open license compatible with AGPL distribution, a genuine italic rather than a slant, and to sit comfortably beside Sora and Manrope. **Now bundled: Fraunces Italic**, SIL Open Font License 1.1, credited in Settings, Licenses. Because that serif renders exactly one fixed line of text at one size in one place, it is pinned to a single static instance (opsz 24, wght 400, SOFT 0, WONK 1) and subset to only the glyphs that line needs rather than shipping the full family: 414,904 bytes down to 5,792 bytes, 18 glyphs. Rebuild it with `tools/subset_fraunces.py`, and rerun that whenever the Brainstorm line changes, because a character outside the subset does not fall back to another face, it simply does not render.

## 5. Shape and layout

- Cards: 20 to 24dp corner radius, one hairline border, soft shadow per theme.
- Buttons: full pill radius. Primary action is a filled accent pill. Secondary is a bordered surface pill. Text buttons in accent color.
- Chips: small pills. Tonal green chips for positive facts, quiet neutral chips (secondary surface, secondary text) for statements like No characters, No roleplay.
- Screen side padding: compact, about 14dp equivalent, so lists breathe wide without touching edges. This was tuned by hand; do not widen it back.
- Grouped settings pattern: sections have a small mono uppercase label, and rows within a section share one card separated by hairline dividers, with a pressed-state tint. Never a floating card per row.
- Scrollable lists fade softly at their top and bottom edges via a mask (transparent to opaque over roughly 12 to 16dp).
- Bottom navigation: four items, in order Projects, Chats, Follow-ups, Discover, each an icon in a small pill that fills with the accent tonal fill when active, label below. The Follow-ups item uses the bookmark icon and carries a small count badge of open items. There is no New nav item: starting a new chat lives on the Chats screen itself (the segmented mode control there), which is what freed the room for Projects. There is no Today tab; it was considered and deliberately canceled (see DECISIONS.md and the Not planned screen), so the navigation stays these four. Settings is reached by the gear in the top brand bar, not the nav bar.
- The brand bar sits above every screen: the mark (breathing) plus the Kam AI wordmark in small quiet type, and the settings gear at the right. When the user is a level deep (Settings, About, Q and A), a back arrow appears at the left of the brand bar.

## 6. Motion

Exactly two spring personalities, mapped to Compose MotionScheme or spring specs:
- Standard spring: damped, no visible bounce (reference cubic-bezier 0.25, 0.9, 0.3, 1). Used for navigation transitions, theme changes, list and layout changes, sheet dismissal, everything by default.
- Expressive spring: slight overshoot (reference cubic-bezier 0.34, 1.45, 0.5, 1). Reserved for signature moments only: the bookmark pop, the follow-up check filling in, the Discover card landing, the nav pill activating, the sheet arriving.

Durations: fast 180ms, medium 340ms, slow 560ms (theme crossfades and large surfaces).

Required behaviors:
- Screen changes slide 26dp in the direction of travel with a fade; going back slides the opposite way. Direction must always match forward and backward.
- Content staggers in after a screen change: header first, then items at roughly 40ms increments.
- Lists rubber-band at their edges with resistance and a springy settle (Android overscroll stretch is acceptable as the native equivalent).
- The bookmark button pops with overshoot and a small rotation when tapped, turns amber, and a toast confirms Saved to Follow-ups.
- Completing a follow-up springs the check in and draws the strikethrough across the full wrapped text (the strike must cover every line, not just the first).
- Discover deal: outgoing card sweeps left with a slight tilt and fade, incoming card rises with a small rotation and settles.
- Chat: message bubbles animate in from below with slight scale; a three-dot typing indicator precedes responses; responses stream in visibly.
- Swipe gestures follow the finger with resistance and spring to their resting position.
- Haptics only where physics implies contact: bookmark pop, check-in, card landing, overscroll edge. Nowhere else.
- Respect the system reduced-motion setting: all animation collapses to instant or near-instant.

Motion restraint is the brand. Calm by default, one small celebration at meaningful moments.

## 7. Components and behaviors

Chats screen: three views switched by a small three-icon pill next to the title. The compact list is the default view. The app remembers whichever view was used last and restores it on every launch. Comfortable list (leading letter icon, title, snippet, time), compact list (tighter rows, no snippet, and no leading letter icon, just title and time), two-column grid (icon, title, two-line snippet). A Pinned section with count and chevron, collapsible, expanded by default, hidden entirely when nothing is pinned, above a Recent label. Every row swipes left to reveal a three-button action rail: Pin or Unpin (tonal green), Archive (neutral), Delete (amber). The rail sits flush at full row height (the earlier clipped and short-button versions were fixed) and is invisible and non-interactive until a drag begins; it must never peek through rounded corners at rest. In grid view, swipe is replaced by a long-press menu with the same actions. Archive and delete collapse the row out with a toast.

Starting a new chat lives on the Chats screen, not the nav bar, and it is the same control that picks the mode: a segmented control sitting directly above the bottom navigation, reachable one-handed, with four segments carrying the full mode names (General, Logic, Brainstorm, Workbench), the type shrinking to fit rather than the words being abbreviated, so a sighted user and a screen reader are told the same name. One tap on a segment starts a fresh conversation in that mode, so an ordinary conversation is still exactly one tap, with General as the resting position. It also drags: the thumb follows the finger, resists past the two ends, snaps to the nearest segment on release, and releasing back on the starting segment selects nothing. The thumb is the only filled element, travels on the expressive spring with overshoot, stretches slightly along its travel, and the arriving segment's dot scales up as its label brightens. Haptics are a light tick as the thumb crosses each detent during a drag and a heavier thump on snap or tap, both routed through the platform haptic feedback so the system setting is respected. Creation stays lazy, so backing out of an unused new chat leaves no empty row.

Each row in the list carries tiny mode dots near the timestamp, about 5dp and 2.5dp apart, one per mode the conversation actually used, in first-use order. They are metadata, not decoration. A colored left bar or spine, a colored border, a background tint, and named text tags were all considered for this and explicitly rejected for making a quiet list loud. Mode names go into each row's accessibility label, since dots say nothing to a screen reader. Filtering by mode is a funnel inside the search field rather than a second row: it opens a sheet listing the four modes plus Discover, multiple choice, with a Show all reset. An active filter fills the funnel with the accent and puts a plain "Showing: <modes>  Clear" line under the search field, so nobody wonders why they are seeing fewer conversations. Filter and search combine, and filtering matches any mode a conversation used, consistent with the dots. Archived conversations are not shown in the main list; they live in a separate Archived view reachable from the screen, where each can be unarchived or deleted. Conversations that belong to a project are excluded from this main list and shown inside their project instead.

Chat screen: a titled header for the open conversation, with an accent bar and a hairline separating it from the messages so the title clearly belongs to the bar above. An overflow menu on the header carries Rename, Move to project (or Remove from project), Archive, and Delete. The interactive mode control lives at the bottom, adjacent to the input, so it is reachable one-handed: a persistent mode indicator (a dot plus the current mode name in its color, with the active model beside it, the model tappable to open its settings) that opens a deliberate mode picker when tapped rather than switching immediately. The picker is a small sheet listing the four modes with color dot, name, and a one-line description, the current one marked, and choosing one applies it and dismisses. Switching keeps the conversation's context and inserts a quiet centerd system notice at the switch point; a one-line mode-colored banner also appears near the top as a switch reminder (with the mode glyph and the mode's one sentence), shown only on an actual switch, never when merely reopening a conversation. Both the banner and the notice fire on every switch in both directions. A grounded Discover discussion hides the mode control and shows its scope banner instead. User bubbles tonal green on the right, AI bubbles surface cards on the left, widened to about 80 percent of screen width so a long answer reads like prose while the left/right asymmetry stays. AI responses render Markdown (headings, bold and italic, lists, inline code and code blocks, links). Under each AI response, a row of small round action buttons: bookmark (save to Follow-ups), copy, share, play (read aloud, which toggles to stop while speaking), regenerate, and an overflow menu whose actions include Report a response, Share thread, and Export. The saved bookmark state is amber with the pop animation. Input is a pill field with placeholder Ask, paste, or talk it out, a microphone button, and a round accent send button. Input stays available while a response is streaming, so a thought can be typed while the model is still writing, and it is still there when the answer lands. Sending is what waits: Stop generation replaces send until the response finishes. An on-device answer can take the better part of a minute, and locking the keyboard for that long to enforce a rule nobody benefits from is worse than allowing the type-ahead. Editing an earlier user message truncates everything after it and re-answers; regenerate replaces the response; there is deliberately no branching.

Mode is per conversation and persists across reopening. Switching mode drops a quiet centered system note into the transcript at the switch point (in both directions), so the history shows exactly where behavior changed. While Logic Partner is active a persistent banner keeps it unmistakable ("Logic Partner is testing your reasoning, not agreeing with it."), using the design-system tonal fill, never amber.

A grounded Discover discussion (started from a moment) states its scope up front with its own banner ("Staying with this passage. Answers come from the text above, not the wider web.", tonal fill, book icon, no amber) and offers a one-tap Continue in open chat. That escape lifts the grounding, turns the conversation into an ordinary open Chat, and drops an honest note that the model may now misremember, so anything that matters is worth checking. The whole history carries forward.

Follow-ups screen: this is the single destination for everything saved anywhere in the app. The bookmark means the same thing everywhere and every saved item lands here, told apart by its source. Open items are cards with a circular checkbox, the saved snippet, and a mono source chip (which mode or surface it came from, when). A source filter row at the top (All, plus one chip per source actually present: General, Logic, Brainstorm, Workbench, Discover, Quick ask) narrows the list, and hides itself entirely when everything came from one source. Each item also carries a small quiet kind chip reading To check or To pursue: things to check are answers that might be wrong and want verifying, things to pursue are ideas worth returning to. The kind is set automatically at save time from where the item came (saving from a Brainstorm conversation defaults to pursue, everything else to check) so saving stays one tap, and the chip is tappable to correct a wrong guess. Deliberately not two screens and not a taxonomy: one list, no due dates, no priorities, no sorting controls. Filtering by kind alongside source is intended and not yet built (issue #33). Completing an item animates it into a Completed group that is collapsed by default with a count, expandable with a chevron flip. Completed items swipe left to remove. Items from a chat link back to their source conversation and can carry an optional note and project link. A saved Discover moment appears here with the Discover source chip and, tapped, reopens as a grounded discussion of its passage. There is no separate saved store anywhere else; the Discover page's own Saved section is a filtered view of this one list, reading the same data.

Discover screen: header with title and a Packs button showing installed count. One moment card at a time: mono topic eyebrow (for example HISTORY, DEALT AT RANDOM), Sora title, a substantial preview passage, a real read of several paragraphs' worth (roughly 120 to 200 words), never a two-line teaser, and a bookmark to save the moment (which saves into the one Follow-ups list). Each card carries the quiet attribution footer reading From Wikipedia, CC BY-SA 4.0. The card is meant to be genuinely read where it stands; Discover is deliberately the opposite of a short-form feed, and brevity for its own sake is a defect here. A Read the full moment line opens the reader sheet with the complete passage, attribution with source link, and two clearly explained choices: Discuss this passage (a grounded chat confined to the text) and Explore this topic (an ordinary open chat), so the scope of each is stated at the point of selection. Below the card: a Quiz me secondary button and a Deal another primary button.

The grounded discussion currently opens in the full chat window carrying the scope banner and the Continue in open chat escape described above. A distinct scoped slide-up surface for it (a smaller presentation that is visibly not a full chat window) is intended but not yet built; it is tracked as an open issue.

Quiz me is a real quiz, not a single throwaway question: three to five questions generated strictly from the full passage, presented one at a time, with the user answering (multiple choice or short answer as fits the question) and getting plain feedback per question, including what the passage actually said when they miss. Because questions come from the full passage, if the user taps Quiz me without having opened the reader for the current card, the app first asks in plain words whether they want to read the full moment before being quizzed on it, with two choices: Read it first (opens the reader) and Quiz me anyway. Once they have opened the reader for that card, Quiz me starts directly with no prompt. A missed question offers one tap to save it to Follow-ups. At the end, a simple result (for example, 4 of 5) and done. The app keeps a quiet running tally, overall and per pack (moments quizzed, questions right out of asked), viewable in a small stats line within Discover. Deliberately excluded: streaks, daily goals, XP, levels, badges, leaderboards, reminders, or any pressure mechanics. The score exists so the user can see their own progress if they care to look, and for no other reason.

A Saved section sits at the bottom of the Discover page listing bookmarked moments; any card can be saved. This section is a filtered view of the single Follow-ups list (the Discover-sourced saved items), not a separate store, so saving in either place is the same act and shows in both. The app tracks drawn card ids locally and only deals unseen cards; if a pack is exhausted it says so plainly and offers a reshuffle. No infinite feed. Depth is one tap away; the single dealt card is the whole surface.

Packs sheet: bottom sheet listing packs with icon, name, moment count, file size, version, and Remove or Get actions, plus the note that packs are offline snapshots downloaded once from GitHub and that nothing about the user is sent. Downloading shows progress and verifies the file hash before install. Installed packs also appear in Settings Storage.

Quick overlay (assistant surface): a bottom sheet over whatever the user was doing, opened by long-pressing power once Kam AI is set as the digital assistant. Contains the mark and name with an on device tag, a text field, a compact answer, a single tap-to-bookmark icon with no note field or extra UI, and an Open Kam AI action. Voice input available. Nothing else; the overlay stays minimal, and refinement of saved items happens later in the full app.

Settings: grouped sections. On this device: Model, Memory, Custom instructions (system-wide instructions the user writes once that apply to every chat, including inside projects), Voice, Storage. Data and connections: Web search, Backup and restore, App lock, Archive old chats, Confirm before deleting a chat, Delete everything (amber label, destructive confirmation, data-only: it wipes conversations, memory, projects, follow-ups, and Discover state, and leaves downloaded models in place to delete individually in Storage). The app: Open with the power button, Power button opens ready for voice, Appearance (theme mode System, Light, or Dark, and the sixteen-color accent picker), a plain "Kam AI can be wrong" notice (the model is small and gets things wrong, check what matters with the bookmark and Follow-ups, and it is not a substitute for a professional), What Kam AI is for (replays onboarding), Questions and answers, About and links. Below the groups, the Support this work button. Back arrow and swipe-back work from every pushed screen; panes slide in the correct direction.

Model screen: the default flow stays one tap, pick a tier. Each model declares its capabilities (text, document attachments, and, when a model supports them, images or voice) as data, so the interface can adapt without hardcoding by model name. The model screen shows those capabilities as small chips that can be tapped for a plain one-line explanation. An Advanced section, visually separated and clearly optional, lists other compatible models to download and switch between; switching the active model keeps every conversation, and deleting the active model falls back to another installed one rather than leaving the app with nothing to answer with. (Still intended here and tracked as open work: measured speed and quality ratings per model, and the input bar hiding controls a model does not support.)

App lock screen: an optional lock on Kam AI itself, separate from the phone's lock, off by default, on top of the always-on at-rest database encryption. Two honestly-labeled strengths chosen with the tradeoff spelled out: Device mode (the phone's own biometric or credential, recoverable, a gate on the app) and Passphrase mode (a separate passphrase that gates the database key itself, genuinely stronger but unrecoverable if forgotten). The lock screen carries a plain, non-nagging Forgot your passphrase path that erases and restarts rather than pretending to recover.

Voice settings: choose TTS engine tier and a specific voice, with at least one male and one female voice at every tier, previewable. STT model tier shown with size. All voice models download with sizes shown first and live in Storage.

Storage: every downloaded artifact (LLM model, STT model, TTS voices, content packs) with sizes and delete actions.

Questions and answers: a searchable list titled Questions and answers, never FAQ in the UI. Live filtering as the user types, matching entries auto-expand, a plain empty state (Nothing matches. Try fewer words.), and a closing line pointing to GitHub issues and hello@kamsiob.com for anything unanswered.

About: mark, name, version line (version, AGPL-3.0, by Kamsiob), then plain link rows all at equal weight: YouTube (@kamsiob), GitHub (github.com/kamsiob), Website (kamsiob.com), Telegram (Kamsiob Lab), Feedback (hello@kamsiob.com), Privacy policy (subtitle: the whole thing is short), Licenses (including the Wikipedia CC BY-SA entry and all open source components). At the bottom, the support line and the Support this work button as the one filled amber element.

Being considered and Not planned screen: reachable from Settings or About. Being considered lists candidate features with no dates and no promises, each naming its real constraint in one line (for example, image understanding needs a separate vision model per tier, a real size and memory cost). Not planned lists cloud sync, accounts, on-screen reading, notification access, and companionship or roleplay features, framed as deliberate decisions. A short expectation-setting line: one person builds this; everything gets read, not everything gets a reply.

Empty states: every list has one, written in the app voice as an invitation to the obvious next action. First-run Chats invites a first question. Empty Follow-ups explains the bookmark and both of its kinds in one line. Discover with no packs points at the Packs button. Never a blank screen.

Built (issue #29): a new conversation's empty state is per mode, not generic. Each carries a faint wash of its mode color, a hand-drawn double-stroke sketch (a chat bubble for General, balance scales for Logic, a brain for Brainstorm, an anvil and hammer for Workbench), and its own nudge in its own type: "So. What's on your mind?" for General, "What claim do you want tested?" for Logic, "All right. What have you got?" for Brainstorm set in the italic serif, and "The result lands here." for Workbench in mono. The sketches are drawn as Compose paths rather than shipped as assets, so they take the mode color directly and cost nothing in the APK; "double stroke" means each shape is drawn twice with the under-stroke offset and lighter. The whole nudge is decorative to a screen reader. General, Logic and Brainstorm appear as chat empty states; Workbench is not a conversation, so its nudge sits where the result will appear. Not shown in the assistant overlay, in Discover, or in a grounded Discover discussion. The right-edge fade on horizontally scrolling chip rows landed in the same pass as `Modifier.edgeFadeHorizontal`, a sibling of the vertical `edgeFade`, and is applied to both Workbench chip rows.

Progress and failure states: model, voice, and pack downloads show progress, survive interruption with resume or clean retry, and verify hashes. A model that fails to load says what happened and what to try in plain words. Errors never apologize theatrically and never go vague.

Toasts: small dark pills, bottom center, one line, used to confirm actions (Saved to Follow-ups, Pinned, Archived, Deleted, pack installed).

## 8. Copy voice

Write like a person explaining something to a friend across a table. Plain words, short sentences, contractions welcome. Never use em dashes in any user-facing copy or documentation; use commas, periods, or colons. No exclamation points. No hype words (unleash, supercharge, empower, magic, delightful). No fear language about being watched. No privacy-brand ad cadence; if a sentence could appear in a VPN commercial, rewrite it. Prefer verifiable claims (Turn on airplane mode and it still works. The code is public.) over slogans. Name tradeoffs instead of hiding them. Buttons say exactly what they do. An action keeps the same name through its whole flow. Interface labels use plain nouns (Questions and answers, not FAQ). Technical identifiers (parameter counts, license names) may appear as secondary mono metadata but the plain-language meaning leads.

Support copy rule: the support button label is Support this work everywhere (it links to the Buy Me a Coffee page). Never coffee or caffeine cliches, never framing that anchors support to small amounts, never begging, never pushing, no ceiling implied, no ask made. The canonical framing: Built and carried by one person. If software made this way matters to you, there's a place to stand behind it. Either way, it's yours.

## 9. Onboarding, final copy

Seven swipeable slides shown on first launch, replayable from Settings under What Kam AI is for. Fully opaque themed background (never transparent over the app). Progress dots that stretch when active and are tappable. Content staggers in per slide. A quiet Skip for now link that disappears on the last slide.

Slide 1. Eyebrow: How it works. Title: Everything happens on your phone. Body: Most AI apps send what you type to a company's computers. Kam AI doesn't. The AI is downloaded onto your phone and runs right there, so your conversations never leave it. Turn on airplane mode and it still works. Below the body, three quiet chips: No characters. No roleplay. No pretend friend. Hero above: the mark with slow ripple rings. Button: Continue.

Slide 2. Eyebrow: What it's for. Title: What you'd actually use it for. Good for list, four items with green check marks: Asking about whatever's on your mind, quick or not. Writing hard messages and cleaning up drafts. Talking out a voice note, getting it back organized. Getting real pushback on your ideas. Not for list, four items with neutral cross marks: Obscure facts. It will get some wrong. Making images. News, scores, live anything, unless you add search. Long research reports and heavy documents. Closing small line: When something matters, bookmark it. It lands in Follow-ups so you can check it properly later. Button: Continue.

Slide 3. Eyebrow: Modes. Title: One AI, four modes. Four rows: General, Everyday questions and back-and-forth. Logic Partner, Argues the other side and pokes holes in your thinking. Brainstorm, Will not hand you ideas, it pulls them out of you. Workbench, Paste something in, get it rewritten, tightened, or reorganized. Small line: Modes are chosen when starting a chat and can be switched at any time. Discover has its own tab. Button: Continue.

Slide 4. Eyebrow: More. The features that are not modes, six rows: the power button, talking instead of typing, attaching a document, Discover, Projects (keep related chats together, under instructions they all follow and notes they all start from), and Follow-ups. Closing line about memory living in Settings.

Slide 5. Eyebrow: Setup. Title: Pick a model that fits. Body: This phone has [detected] GB of memory. Bigger models give better answers but need more room. [Recommended tier] fits comfortably here. **One card, the recommended tier**, with its name, its mono download size and a Recommended badge, and a quiet See the other options link that reveals the full tier list in place. Locked tiers in that list are grayed with an amber Needs [N] GB note. The recommendation leaves headroom, never maxing out the device. Leading with one card rather than a menu is deliberate: the choice arrives at the moment the user knows least about it, and the recommendation is already computed from real memory. Small line: A read-aloud voice can be picked later in Settings, male or female. Button: Download [tier] and its size, replaced by a progress bar and Continue once it starts.

Before any large download starts, the app checks what it is about to spend and says so: not enough disk stops with both real numbers and the offer of a smaller tier; a metered connection asks first with the size stated, waiting for wifi as the default and Use mobile data anyway as the named alternative; a low battery with no charger is mentioned rather than refused. A download interrupted by process death resumes on its own once the connection is one that costs nothing, and never onto mobile data.

Slide 6. Eyebrow: Optional. Title: Two things you can add. Intro: Both are optional and both can be added later in Settings. The model is downloading in the background either way. Two cards, each declinable on its own, neither started automatically. Speaking instead of typing, with the combined size of the recommended speech setup for this phone and an Add it button; once added it reads Queued after the model, because the model is what makes the app work at all. Something to read, describing Discover honestly and pointing at the Discover tab rather than offering a pack, since packs are chosen by topic and nobody five minutes in knows which they want. Closing line: Models, voices and packs all live in Settings if you want to change or add anything later. Said once, never repeated.

Slide 7. Eyebrow: What it costs. Title: Nothing. No catch. Body: Everything is included. No locked features, no subscription, no ads, no account to make. The code is public, and the license means it has to stay open. That's a rule, not a promise. Small line: Kam AI is built and carried by one person. If software made this way matters to you, there's a place to stand behind it. Either way, it's yours. Buttons: Support this work (amber), then Start using Kam AI (primary).

## 9b. Getting a model onto the device, and never meeting a dead chat

Onboarding can be skipped, and skipping must not leave a chat screen that does
nothing. A chat with no model installed carries the offer itself, centerd in the
empty space: the title Kam AI needs a model to answer, the same honest line about
this phone's memory that the setup slide uses, the recommended model's name with
its mono size, one Download it button, and a quiet See the other options link. It
is hidden while a download is already running, because the app-wide indicator
already says so.

Typing before a model is ready is never refused. The message stays in the
transcript and is sent the moment a model loads, with a notice saying that is
what is happening. The alternative, explaining and refusing with a time estimate,
is more precise and worse: somebody who has just typed a question wants the
question asked, not an estimate.

## 9c. The download indicator

One indicator for models, voices and content packs, in the shell under the brand
bar, so it is on every screen including chat, Settings and Discover. A slim line,
never a banner: it is on screen for minutes at a time and has to be the kind of
thing you stop noticing. It expands and shrinks with the standard spring so
content below never jumps under a finger. It shows what is downloading, the
percentage and the size remaining, and tapping it opens Storage, where the same
downloads appear with pause, resume and cancel. Several at once show the current
one and how many are behind it. Paused and verifying both count as active;
finished has its own quiet toast and failed belongs where it can be acted on.

## 10. Questions and answers, launch content

Ship these entries, in this voice, plus the modes entry, the Projects entry (what a project is, that instructions tell it how to behave and notes are background it should already know, that both apply only inside that project, and that moving a chat in applies from that point on rather than backwards) and the Memory entry (only what you ask it to unless automatic memory is on, everything listed and deletable in Settings, and an answer that used memory says so underneath). Does anything I type leave my phone? No. The AI runs on your phone, not on a server. Kam AI only touches the internet when you ask it to, like downloading a model or a content pack, or if you set up web search yourself. Why does it sometimes get facts wrong? The model is small enough to fit on a phone, so it knows less than the big cloud AIs and can misremember things like dates and names. When an answer matters, bookmark it and check later. That is exactly what Follow-ups are for. Why can't it make images? Making images needs a different, much heavier kind of AI model. Kam AI sticks to text on purpose so it can run well on your phone. What do the model choices mean? Bigger models give better answers but need more memory. Kam AI checks how much memory your phone has and recommends the best fit. You can switch any time in Settings. What are the modes? Four ways to use the same AI. General is everyday back-and-forth. Logic Partner argues the other side of your ideas. Brainstorm will not hand you ideas, it pulls them out of you. Workbench rewrites and reorganizes text you paste in. You pick a mode when you start a chat and can switch at any time from the mode name above the message box. Discover is separate, with its own tab: it deals you something interesting to read and talk about. How do I open it with the power button? Make Kam AI your phone's assistant app: open your phone's Settings, then Apps, then Default apps, then Digital assistant app. After that, a long press on the power button opens the quick panel. How do I change the voice? Settings, then Voice. Pick a voice you like, male or female. Voices are downloads, so you'll see the size before anything happens. What is a follow-up? A bookmark you can put on any answer you want to double-check or dig into later. Saved things collect in the Follow-ups tab so nothing gets lost. What are content packs? Small offline bundles of short reads for Discover, built from Wikipedia. Download the topics you want, remove them whenever. Once downloaded, they work without internet. How do I move everything to a new phone? Settings, then Backup and restore. Export puts everything into one file. Bring that file to the new phone, import it, and you're back where you left off. Will there be cloud sync or accounts? No, and that's a decision, not a gap. Your conversations stay yours, on your device. Backup and restore covers moving between phones.

## 11. Accessibility and quality floor

Minimum touch targets 48dp. Visible focus for keyboard and switch access. Full TalkBack labels on all controls including the bookmark, play, and swipe actions (swipe actions must also be reachable through the accessibility actions menu). Contrast meeting WCAG AA in both themes, checked especially for tertiary text on surfaces; the sixteen accent colors are contrast-checked in both themes by a test that fails the build if one regresses. Dynamic type respected without breaking layouts. Reduced motion respected. Color is never the only carrier of meaning (saved items also carry the bookmark icon, locked tiers also say why in text).
