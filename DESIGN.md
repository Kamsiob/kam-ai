# DESIGN.md · Kam AI by Kamsiob

This document is the single source of truth for how Kam AI looks, moves, and speaks. Every screen, component, and line of copy must be checked against it. When code and this document disagree, this document wins. It must be committed to the repository and kept updated as decisions change.

## 1. What this app is

Kam AI is a fully local AI app for Android. The AI model is downloaded onto the phone and runs there. Conversations, memory, projects, and everything else stay on the device. The app is free, open source under AGPLv3, has no accounts, no ads, no subscriptions, and collects zero data.

Positioning, to be carried into every store listing, README, and description: Kam AI is a private thinking and drafting tool for things that should not leave the device. It is good at transforming, organizing, and rephrasing text the user gives it, at everyday questions, and at pushing back on ideas. It is explicitly NOT positioned as a private ChatGPT and must never imply it matches large cloud model capability. Small on-device models know less, get some facts wrong, cannot make images, and are weaker at long polished documents. The app says this plainly and builds features around it (the Follow-up flag) instead of hiding it.

Hard identity rules, non-negotiable in every mode and every piece of copy: no characters, no roleplay, no pretend companion, no emotional manipulation, no hyping, no getting personal, no sycophancy. These are stated design commitments, visible to users, not just internal rules.

## 2. The mark

The Kam AI mark is a lit core held inside a broken ring, with a smaller inner arc as a counterweight.

Geometry, on a 48 by 48 viewbox: outer ring at radius 16, stroke width 3.4, rounded caps, drawn as a 270 degree arc so a 90 degree gap sits at the top right. Inner accent arc at radius 10.5, stroke 2.2, about 55 degrees long, rotated 150 degrees, at 55 percent opacity. Core is a circle at radius 6.6 filled with a radial gradient from a pale mint highlight at the upper left (#C9F5DB) through the mid green (#4FBF85) to a deep green edge (#1F6B44).

Meaning, usable in docs and marketing: the ring is enclosure, your data held on the device. The gap is the one deliberate opening, the things the user chooses to let out (downloads, opt-in search). The core is the on-device light, the indicator that the machine doing the thinking is this one.

Rules: the ring and inner arc take the current theme accent color. The core gradient never changes. The mark may breathe (slow scale to 1.07 with a soft green drop shadow over a 4.2 second ease-in-out loop) when used as a status indicator. It appears small and quiet at the top of every screen next to the wordmark, never as a large blocking element outside of onboarding and About.

App icon: the same geometry as an Android adaptive icon. Foreground is the mark, background is the theme ivory (light) with the deep pine variant used where a dark background is required. The Android 13+ themed monochrome icon variant is the ring, inner arc, and a solid core dot in single color, which this geometry supports natively. No thin details that die at 48 pixels.

## 3. Color

One green accent. Amber is reserved exclusively for three things: follow-up flags, locked model tiers, and the Support this work button. Amber must never appear anywhere else. No purple anywhere. No gradients except the core of the mark. No sparkles, no glow effects beyond the mark's breathing shadow and the onboarding ripple rings.

Light theme (warm ivory):
- Background: #F6F4EC
- Surface (cards): #FFFFFF
- Secondary surface: #EDEDE2
- Tonal fill (green tint): #E2EDE0, with tonal text #2A5C42
- Text primary: #1B241E
- Text secondary: #61705F
- Text tertiary: #95A093
- Accent: #2E7A52, with on-accent text #F2FBF4
- Flag amber: #C98A22, with soft amber fill #F7EBD3
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
- Accent: #6FD19E, with on-accent text #0A1B11
- Flag amber: #E4B05A, with soft amber fill #2E2515
- Borders: rgba(237,242,234,0.07)
- Card shadow: 0 5px 16px rgba(0,0,0,0.18). Dark shadows must stay this soft. Heavy black shadows in dark mode read as dirty translucent boxes and were explicitly rejected.

Backgrounds are never pure black or pure white. Theme switching crossfades all surfaces together over the slow duration; nothing snaps.

## 4. Typography

- Display face: Sora, weights 600 and 700, for screen titles, card titles, tier names, mode names, the wordmark. Letter spacing slightly negative (about -0.02em to -0.03em) at large sizes.
- Body face: Manrope, weights 400 to 800, for everything else.
- Utility mono: JetBrains Mono, for specs and metadata only: RAM figures, download sizes, versions, timestamps, source chips, section eyebrow labels. Mono signals a fact about the machine.
- Never use Inter, Roboto, or Open Sans as display faces. Body text at readable sizes, roughly 14 to 16sp equivalents in the real app; metadata smaller but never below accessible minimums. Weight carries hierarchy, not extra families.

## 5. Shape and layout

- Cards: 20 to 24dp corner radius, one hairline border, soft shadow per theme.
- Buttons: full pill radius. Primary action is a filled accent pill. Secondary is a bordered surface pill. Text buttons in accent color.
- Chips: small pills. Tonal green chips for positive facts, quiet neutral chips (secondary surface, secondary text) for statements like No characters, No roleplay.
- Screen side padding: compact, about 14dp equivalent, so lists breathe wide without touching edges. This was tuned by hand; do not widen it back.
- Grouped settings pattern: sections have a small mono uppercase label, and rows within a section share one card separated by hairline dividers, with a pressed-state tint. Never a floating card per row.
- Scrollable lists fade softly at their top and bottom edges via a mask (transparent to opaque over roughly 12 to 16dp).
- Bottom navigation: four items (Chats, New, Discover, Follow-ups), each an icon in a small pill that fills with tonal green when active, label below. Settings is reached by the gear in the top brand bar, not the nav bar.
- The brand bar sits above every screen: the mark (breathing) plus the Kam AI wordmark in small quiet type, and the settings gear at the right. When the user is a level deep (Settings, About, Q and A), a back arrow appears at the left of the brand bar.

## 6. Motion

Exactly two spring personalities, mapped to Compose MotionScheme or spring specs:
- Standard spring: damped, no visible bounce (reference cubic-bezier 0.25, 0.9, 0.3, 1). Used for navigation transitions, theme changes, list and layout changes, sheet dismissal, everything by default.
- Expressive spring: slight overshoot (reference cubic-bezier 0.34, 1.45, 0.5, 1). Reserved for signature moments only: the flag pop, the follow-up check filling in, the Discover card landing, the nav pill activating, the sheet arriving.

Durations: fast 180ms, medium 340ms, slow 560ms (theme crossfades and large surfaces).

Required behaviors:
- Screen changes slide 26dp in the direction of travel with a fade; going back slides the opposite way. Direction must always match forward and backward.
- Content staggers in after a screen change: header first, then items at roughly 40ms increments.
- Lists rubber-band at their edges with resistance and a springy settle (Android overscroll stretch is acceptable as the native equivalent).
- The flag button pops with overshoot and a small rotation when tapped, turns amber, and a toast confirms Saved to Follow-ups.
- Completing a follow-up springs the check in and draws the strikethrough across the full wrapped text (the strike must cover every line, not just the first).
- Discover deal: outgoing card sweeps left with a slight tilt and fade, incoming card rises with a small rotation and settles.
- Chat: message bubbles animate in from below with slight scale; a three-dot typing indicator precedes responses; responses stream in visibly.
- Swipe gestures follow the finger with resistance and spring to their resting position.
- Haptics only where physics implies contact: flag pop, check-in, card landing, overscroll edge. Nowhere else.
- Respect the system reduced-motion setting: all animation collapses to instant or near-instant.

Motion restraint is the brand. Calm by default, one small celebration at meaningful moments.

## 7. Components and behaviors

Chats screen: three views switched by a small three-icon pill next to the title. The compact list is the default view. The app remembers whichever view was used last and restores it on every launch. Comfortable list (leading letter icon, title, snippet, time), compact list (tighter rows, no snippet, and no leading letter icon, just title and time), two-column grid (icon, title, two-line snippet). A Pinned section with count and chevron, collapsible, expanded by default, hidden entirely when nothing is pinned, above a Recent label. Every row swipes left to reveal a three-button action rail: Pin or Unpin (tonal green), Archive (neutral), Delete (amber). The rail is invisible and non-interactive until a drag begins; it must never peek through rounded corners at rest. In grid view, swipe is replaced by a long-press menu with the same actions. Archive and delete collapse the row out with a toast.

Chat screen: mode switcher as a segmented pill (Chat, Logic, Bench) with a sliding thumb. Model tier shown quietly. User bubbles tonal green on the right, AI bubbles surface cards on the left. Under each AI response, a row of small round action buttons: flag, copy, play (read aloud), regenerate. Flagged state is amber with the pop animation. Input is a pill field with placeholder Ask, paste, or talk it out, a microphone button, and a round accent send button. Stop generation replaces send while streaming. Editing an earlier user message truncates everything after it and re-answers; regenerate replaces the response; there is deliberately no branching.

Follow-ups screen: open items as cards with a circular checkbox, the flagged snippet, and a mono source chip (which mode or surface it came from, when). Completing an item animates it into a Completed group that is collapsed by default with a count, expandable with a chevron flip. Completed items swipe left to remove. Items link back to their source conversation and can carry an optional note and project link added in the full app.

Discover screen: header with title and a Packs button showing installed count. One moment card at a time: mono topic eyebrow (for example HISTORY, DEALT AT RANDOM), Sora title, and a substantial preview passage, a real read of several paragraphs' worth (roughly 120 to 200 words), never a two-line teaser. Each card carries the quiet attribution footer reading From Wikipedia, CC BY-SA 4.0. The card is meant to be genuinely read where it stands; Discover is deliberately the opposite of a short-form feed, and brevity for its own sake is a defect here. A Read the full moment line opens the reader sheet with the complete passage, attribution with source link, and a Discuss this moment button that starts a grounded chat. Below the card: a Quiz me secondary button and a Deal another primary button.

Quiz me is a real quiz, not a single throwaway question: three to five questions generated strictly from the full passage, presented one at a time, with the user answering (multiple choice or short answer as fits the question) and getting plain feedback per question, including what the passage actually said when they miss. Because questions come from the full passage, if the user taps Quiz me without having opened the reader for the current card, the app first asks in plain words whether they want to read the full moment before being quizzed on it, with two choices: Read it first (opens the reader) and Quiz me anyway. Once they have opened the reader for that card, Quiz me starts directly with no prompt. A missed question offers one tap to flag it for follow-up. At the end, a simple result (for example, 4 of 5) and done. The app keeps a quiet running tally, overall and per pack (moments quizzed, questions right out of asked), viewable in a small stats line within Discover. Deliberately excluded: streaks, daily goals, XP, levels, badges, leaderboards, reminders, or any pressure mechanics. The score exists so the user can see their own progress if they care to look, and for no other reason.

A Saved section sits at the bottom of the Discover page listing bookmarked moments; any card can be saved. The app tracks drawn card ids locally and only deals unseen cards; if a pack is exhausted it says so plainly and offers a reshuffle. No infinite feed. Depth is one tap away; the single dealt card is the whole surface.

Packs sheet: bottom sheet listing packs with icon, name, moment count, file size, version, and Remove or Get actions, plus the note that packs are offline snapshots downloaded once from GitHub and that nothing about the user is sent. Downloading shows progress and verifies the file hash before install. Installed packs also appear in Settings Storage.

Quick overlay (assistant surface): a bottom sheet over whatever the user was doing, opened by long-pressing power once Kam AI is set as the digital assistant. Contains the mark and name with an on device tag, a text field, a compact answer, a single tap-to-flag icon with no note field or extra UI, and an Open Kam AI action. Voice input available. Nothing else; the overlay stays minimal, and refinement of flags happens later in the full app.

Settings: three grouped sections. On this device: Model, Voice, Storage. Data and connections: Web search, Backup and restore, Delete everything (amber label, destructive confirmation). The app: What Kam AI is for (replays onboarding), Questions and answers, About and links. Below the groups, the Support this work button. Back arrow and swipe-back work from every pushed screen; panes slide in the correct direction.

Voice settings: choose TTS engine tier and a specific voice, with at least one male and one female voice at every tier, previewable. STT model tier shown with size. All voice models download with sizes shown first and live in Storage.

Storage: every downloaded artifact (LLM model, STT model, TTS voices, content packs) with sizes and delete actions.

Questions and answers: a searchable list titled Questions and answers, never FAQ in the UI. Live filtering as the user types, matching entries auto-expand, a plain empty state (Nothing matches. Try fewer words.), and a closing line pointing to GitHub issues and hello@kamsiob.com for anything unanswered.

About: mark, name, version line (version, AGPL-3.0, by Kamsiob), then plain link rows all at equal weight: YouTube (@kamsiob), GitHub (github.com/kamsiob), Website (kamsiob.com), Telegram (Kamsiob Lab), Feedback (hello@kamsiob.com), Privacy policy (subtitle: the whole thing is short), Licenses (including the Wikipedia CC BY-SA entry and all open source components). At the bottom, the support line and the Support this work button as the one filled amber element.

Being considered and Not planned screen: reachable from Settings or About. Being considered lists candidate features with no dates and no promises, each naming its real constraint in one line (for example, image understanding needs a separate vision model per tier, a real size and memory cost). Not planned lists cloud sync, accounts, on-screen reading, notification access, and companionship or roleplay features, framed as deliberate decisions. A short expectation-setting line: one person builds this; everything gets read, not everything gets a reply.

Empty states: every list has one, written in the app voice as an invitation to the obvious next action. First-run Chats invites a first question. Empty Follow-ups explains the flag in one line. Discover with no packs points at the Packs button. Never a blank screen.

Progress and failure states: model, voice, and pack downloads show progress, survive interruption with resume or clean retry, and verify hashes. A model that fails to load says what happened and what to try in plain words. Errors never apologize theatrically and never go vague.

Toasts: small dark pills, bottom center, one line, used to confirm actions (Saved to Follow-ups, Pinned, Archived, Deleted, pack installed).

## 8. Copy voice

Write like a person explaining something to a friend across a table. Plain words, short sentences, contractions welcome. Never use em dashes in any user-facing copy or documentation; use commas, periods, or colons. No exclamation points. No hype words (unleash, supercharge, empower, magic, delightful). No fear language about being watched. No privacy-brand ad cadence; if a sentence could appear in a VPN commercial, rewrite it. Prefer verifiable claims (Turn on airplane mode and it still works. The code is public.) over slogans. Name tradeoffs instead of hiding them. Buttons say exactly what they do. An action keeps the same name through its whole flow. Interface labels use plain nouns (Questions and answers, not FAQ). Technical identifiers (parameter counts, license names) may appear as secondary mono metadata but the plain-language meaning leads.

Support copy rule: the support button label is Support this work everywhere (it links to the Buy Me a Coffee page). Never coffee or caffeine cliches, never framing that anchors support to small amounts, never begging, never pushing, no ceiling implied, no ask made. The canonical framing: Built and carried by one person. If software made this way matters to you, there's a place to stand behind it. Either way, it's yours.

## 9. Onboarding, final copy

Five swipeable slides shown on first launch, replayable from Settings under What Kam AI is for. Fully opaque themed background (never transparent over the app). Progress dots that stretch when active and are tappable. Content staggers in per slide. A quiet Skip for now link that disappears on the last slide.

Slide 1. Eyebrow: How it works. Title: Everything happens on your phone. Body: Most AI apps send what you type to a company's computers. Kam AI doesn't. The AI is downloaded onto your phone and runs right there, so your conversations never leave it. Turn on airplane mode and it still works. Below the body, three quiet chips: No characters. No roleplay. No pretend friend. Hero above: the mark with slow ripple rings. Button: Continue.

Slide 2. Eyebrow: What it's for. Title: What you'd actually use it for. Good for list, four items with green check marks: Asking about whatever's on your mind, quick or not. Writing hard messages and cleaning up drafts. Talking out a voice note, getting it back organized. Getting real pushback on your ideas. Not for list, four items with neutral cross marks: Obscure facts. It will get some wrong. Making images. News, scores, live anything, unless you add search. Long research reports and heavy documents. Closing small line: When something matters, flag it. It lands in Follow-ups so you can check it properly later. Button: Continue.

Slide 3. Eyebrow: Modes. Title: One AI, four modes. Four rows: Chat, Everyday questions and back-and-forth. Logic Partner, Argues the other side and pokes holes in your thinking. Workbench, Paste something in, get it rewritten, tightened, or reorganized. Discover, Deals you something interesting to read and talk about. Small line: Switch with the pills at the top of any chat. Discover has its own tab. Button: Continue.

Slide 4. Eyebrow: Setup. Title: Pick a model that fits. Body: This phone has [detected] GB of memory. Bigger models give better answers but need more room. [Recommended tier] fits comfortably here. Three tier cards with names, mono download sizes, a Recommended badge on the suggested tier, and locked tiers greyed with an amber Needs [N] GB note. The recommendation leaves headroom, never maxing out the device. Small line: A read-aloud voice can be picked later in Settings, male or female. Button: Download [tier] and its size.

Slide 5. Eyebrow: What it costs. Title: Nothing. No catch. Body: Everything is included. No locked features, no subscription, no ads, no account to make. The code is public, and the license means it has to stay open. That's a rule, not a promise. Small line: Kam AI is built and carried by one person. If software made this way matters to you, there's a place to stand behind it. Either way, it's yours. Buttons: Support this work (amber), then Start using Kam AI (primary).

## 10. Questions and answers, launch content

Ship these entries, in this voice, plus the modes entry. Does anything I type leave my phone? No. The AI runs on your phone, not on a server. Kam AI only touches the internet when you ask it to, like downloading a model or a content pack, or if you set up web search yourself. Why does it sometimes get facts wrong? The model is small enough to fit on a phone, so it knows less than the big cloud AIs and can misremember things like dates and names. When an answer matters, flag it and check later. That is exactly what Follow-ups are for. Why can't it make images? Making images needs a different, much heavier kind of AI model. Kam AI sticks to text on purpose so it can run well on your phone. What do the model choices mean? Bigger models give better answers but need more memory. Kam AI checks how much memory your phone has and recommends the best fit. You can switch any time in Settings. What are the modes? Four ways to use the same AI. Chat is everyday back-and-forth. Logic Partner argues the other side of your ideas. Workbench rewrites and reorganizes text you paste in. Discover deals you something interesting to read and talk about. Switch with the pills at the top of a chat; Discover has its own tab. How do I open it with the power button? Make Kam AI your phone's assistant app: open your phone's Settings, then Apps, then Default apps, then Digital assistant app. After that, a long press on the power button opens the quick panel. How do I change the voice? Settings, then Voice. Pick a voice you like, male or female. Voices are downloads, so you'll see the size before anything happens. What is a follow-up? A small flag you can put on any answer you want to double-check or dig into later. Flagged things collect in the Follow-ups tab so nothing gets lost. What are content packs? Small offline bundles of short reads for Discover, built from Wikipedia. Download the topics you want, remove them whenever. Once downloaded, they work without internet. How do I move everything to a new phone? Settings, then Backup and restore. Export puts everything into one file. Bring that file to the new phone, import it, and you're back where you left off. Will there be cloud sync or accounts? No, and that's a decision, not a gap. Your conversations stay yours, on your device. Backup and restore covers moving between phones.

## 11. Accessibility and quality floor

Minimum touch targets 48dp. Visible focus for keyboard and switch access. Full TalkBack labels on all controls including the flag, play, and swipe actions (swipe actions must also be reachable through the accessibility actions menu). Contrast meeting WCAG AA in both themes, checked especially for tertiary text on surfaces. Dynamic type respected without breaking layouts. Reduced motion respected. Color is never the only carrier of meaning (flags also have the flag icon, locked tiers also say why in text).
