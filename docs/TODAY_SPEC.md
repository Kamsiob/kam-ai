# Today tab — full specification (DEFERRED)

Owner directive: "Build this only when directed; it is a substantial addition and should
not interrupt finishing and shipping the core application." Do NOT build until the owner
says so. Conforms to DESIGN.md and MASTER_SPEC.md standing rules (no em dashes, two-spring
motion, amber only for saved/locked/support, honesty voice, tested increments, DECISIONS.md
log, standard closing steps). This doc is the authoritative capture of the spec.

## 1. What Today is
A daily newspaper assembled overnight on the user's own phone from sources the user chose.
Not a feed: arrives once, has a front page, ends. No infinite scroll, no refresh-for-more,
no notifications, nothing changes while reading. The phone gathers the news; the local model
reads each item and writes a short summary. Nothing about what the user reads leaves the
device (no server exists to receive it). Division of labour, must be preserved: the model is
never asked to know anything about the world, only to read supplied text and organize it.
Lives in bottom nav alongside Chats, Projects, Discover, Saved (5 items — verify legibility
at large font sizes; consider shortening a label).

## 2. Setup
First open: pick sources from a topic-organized catalog (same interaction as installing
Discover packs; never shows a feed address). Unknown source: type a site name, app discovers
the feed from the page; if none found, say so plainly. Enter a few interest keywords (e.g.
Linux, AMD, privacy legislation). Choose stories per topic (~5-10). Intent: a finite 10-15 min
morning read. Catalog is a versioned GitHub release asset (same pattern as Discover manifests
and models). No backend.

## 3. Overnight build
While charging and idle: fetch latest items from chosen sources, fetch FULL text of each
article, model reads + summarizes each, assemble edition.
CRITICAL PRIVACY INVARIANT: full text of every article is fetched during the overnight build,
before the user chooses anything to read. NEVER fetch on demand. This is what makes reading
unobservable — the article is already on the phone, so opening it in the morning makes no
network request; the publisher learns nothing about which stories were read, in what order,
or how long. Anyone optimising this to fetch-on-tap silently destroys the privacy property
while everything appears to work. Write this into DECISIONS.md AND a code comment at the fetch
site. Same reason: remote images never load while reading — fetch during build or do not show.
Build is time-boxed, thermally aware, interruptible; stops if unplugged; if it cannot finish,
publishes what it has and says so plainly. If it did not run, opening Today offers to build now
with visible progress from the first moment and a real cancel; state the reason plainly (e.g.
phone was not charging overnight).

## 4. Front page (visual spec)
Core principle: not every item is the same size. Varying scale expresses importance without a
label; it is what separates a newspaper from a feed.
- Masthead: "Today" in display face ~34-36sp, weight 700, letter-spacing ~-0.05em, tight line
  height. Below: one row in utility mono ~8sp uppercase, letter-spacing ~0.15em, secondary
  colour, date left / story count right (e.g. "Thu, Jul 23, 2026" and "16 stories"). Beneath:
  solid 2px rule in primary text colour full content width; 4px below it a hairline rule at
  ~32% opacity. That double rule is the newspaper signal, keep it crisp.
- Lead story: topic kicker in mono ~7.5sp uppercase letter-spacing 0.16em accent colour;
  headline display ~21sp weight 700 letter-spacing -0.032em line-height ~1.14; two-sentence
  summary body ~10.5sp line-height 1.65 secondary; source row: publisher name in small mono
  chip on secondary surface uppercase ~7sp + relative time in mono tertiary. Coverage note (if
  any) below in mono ~7sp with a small circular info icon.
- "Matched to your interests": block on tonal green fill, inset ~12dp, radius ~19dp, soft card
  shadow. Mono label "Matched to your interests" tonal text uppercase ~7.5sp letter-spacing
  0.16em. Then 2-3 items, headline ~12.5sp display + source row, hairline dividers low opacity.
  This is where the user's keywords surface.
- Sections: intro row = mono label uppercase ~7.5sp letter-spacing 0.2em tertiary + hairline
  rule filling remaining width. Names: World, Health, Technology, Linux and open source,
  Science, Games, Business, Privacy and security.
- Section items: first item may get medium prominence (headline ~15sp + one-sentence summary);
  rest compact: two-digit index in mono tertiary ~7.5sp left, headline ~12.5sp + source row,
  hairline rules between. Compact items carry NO summaries (deliberate density rhythm).
- Ending: centered line in mono ~8sp uppercase letter-spacing 0.16em tertiary: "that's the
  edition." Nothing after it that invites more scrolling.
- Background: a slightly warmer page tone between app background and surface; define as a token
  in both themes.
- Motion: content staggers in on first appearance, header first then blocks at ~40ms increments
  with standard damped spring; tapping a story presses to ~0.985 scale + faint tint; edge fade
  top/bottom; collapses to instant under reduced motion.

## 5. Reading an article
Tap slides in article view (standard directional transition; back = opposite slide). Shows
topic kicker, publisher's own headline unchanged (display ~18.5sp), meta row (publisher chip,
relative time, author if available). Then mono accent uppercase label "Summary" + model summary
in body ~11sp line-height ~1.7. Below: 1-2 transparency notes, small cards on surface colour,
hairline border, radius ~15dp, mono ~8sp line-height 1.7. First always: "Summarized on your
phone. The headline is the publisher's own, unchanged. Kam AI describes what the article says,
not whether it is right." Second only when relevant, in amber: coverage note e.g. "Coverage gap.
Only 1 of your 11 sources covered this story." Action row of pills: "Read full article" (filled
accent primary), "Discuss" (bordered secondary), "Save" (amber soft pill, fills solid amber with
a small pop + rotation, label -> "Saved"). Bottom: centered link to open original in mono ~7.5sp,
directly beneath in dimmer text: "you leave Kam AI, that site's own rules apply there" (at the
point of departure, not buried in a policy screen). Full article renders in app's own typography,
no ads/trackers/remote images; original link always available.

## 6. Discussing an article
Full article text is on-device, so discussion is fully offline, no web search (the opt-in BYO
endpoint search stays off and unrelated). Surface: a sheet sliding up over the article, ~77% of
screen height, top corners radius ~27dp, grab handle, scrim behind; springs up with expressive
spring over ~460ms; deliberately not full screen so the reader stays located in the article.
Header on tonal green fill: mono label "Discussing this article" + small icon, article title
display ~12sp, and before any question a line in tonal text ~8.5sp: "Kam AI is working only from
this article. Anything beyond it, save it for later." Scope stated up front, never discovered by
collision. Conversation uses normal bubbles, messages animate in from below with slight scale.
Out-of-article question: model says so plainly (app voice, e.g. "That is outside this article. It
only covers the AMD patches and what they include, so I would be guessing."). Immediately beneath:
two pills — "Save this question" (amber) and "Ask in a full chat" (bordered secondary). Saving
confirms in place; full chat carries question + article context across (no retyping). Conversation
stored in normal chat history, reachable later, but lives in this scoped sheet while in use.

## 7. Saved integration
Any article and any out-of-article question can be saved to the existing Saved list. Today becomes
another filter alongside Chat, Logic, Discover. Saved entries carry publisher + time in source chip.

## 8. About Today screen
Three entry points: info icon in Today header; Settings > app section; a card shown once during
first-run setup (dismissible, revisitable). Sequence of distinct visual blocks, generous spacing,
each scannable in ~2s.
- Block 1 opening: Kam AI mark breathing ~36dp centered; display ~19sp "A newspaper that reads
  itself, on your phone."; body "Your sources, gathered overnight, summarized here, and read by
  nobody but you."
- Block 2 how it works, 3 numbered step cards (surface colour, hairline border, radius ~17dp,
  large mono numeral accent, bold display label ~12sp, one body line ~9.5sp): 01 Overnight "while
  your phone charges, it gathers your sources and reads them." 02 Morning "an edition is waiting,
  sized to what you asked for." 03 Nowhere else "none of it is uploaded, because there is nowhere
  for it to go."
- Block 3 comparison (centrepiece): compact 3-column table, header row, hairline rules between
  rows, generous vertical padding, row labels mono uppercase ~7sp tertiary down the left. Columns:
  "Reading here" (accent colour + subtle tonal bg tint on its column), "In a typical news app"
  (neutral secondary), "Opening the site itself" (neutral secondary). Do NOT name any company.
  Rows: (Who knows which stories you read) here "No one. Opening a story makes no request." /
  typical "The company behind it, and usually its partners." / site "The site, and any trackers on
  the page." | (What your source list reveals) here "Each publisher sees one feed request. No one
  sees the whole list." / typical "Your full reading list sits on their servers." / site "Each
  visit is logged with your address." | (Where summaries are made) here "On your phone." / typical
  "On their servers." / site "Not applicable." | (Accounts) here "None." / typical "Usually
  required." / site "Sometimes required." | (Cookies and identifiers) here "None." / typical
  "Standard." / site "Standard." | (Ads while reading) here "None." / typical "Common." / site
  "Common." | (If the company disappears) here "Nothing changes. It runs on your phone." / typical
  "Your history goes with it." / site "Not applicable." Below the table, one body line ~10sp: "Kam
  AI cannot see any of this either. There is no server on our side to see it with." Tone rule: every
  cell states a factual property of a method, never a judgment about a company; no competitor named;
  never anything like "do not use other apps."
- Block 4 how sources are chosen: three short titled blocks (mono accent heading + 2-3 sentences):
  "Reporting, not commentary" (sources whose work is finding out what happened; newsroom, named
  journalists, corrections; opinion/analysis left out because a summary of an argument reads like a
  summary of a fact). "Wires first for world news" (catalog leans on wire services for politics/world
  events; their business is selling the same account to newspapers of every stripe). "Your list in
  the end" (catalog is a starting point, not a fence; add/remove anything; edition follows your
  list). Never name an outlet as unacceptable, never enumerate exclusions, never take a political
  position.
- Block 5 honest note (note style card): "When you tap through to an original page, you leave Kam AI
  and land on the open web, where that site's own privacy policy applies, the same as any link in any
  app. Everything before that point stayed on your phone."
- Block 6 hardening summary (compact mono note): "No cookies. No identifiers. No images loaded while
  you read. Tracking parameters stripped from every link. No account, ever."

## 9. Source catalog & editorial standard
Reputable outlets whose work is reporting. Verify every feed before including (feeds move); prefer
feeds carrying usable article text over headline-only stubs. Topics at launch: Technology, Linux and
open source, Privacy and security, Science, Health, Business and economics, World, Games, Productivity
and work. Candidate sources to VERIFY (not fixed): Ars Technica, The Verge (tech); Phoronix, LWN,
It's FOSS, OMG Ubuntu (Linux); Krebs on Security, Bleeping Computer, The Record (privacy/security);
Quanta, Nature news, Science Daily, NASA (science); STAT etc. (health, small/clinical); wire business
desks (business); AP, Reuters, AFP (world) + BBC, Deutsche Welle, France 24, Al Jazeera; Eurogamer etc.
(games). No dedicated politics category — world via wires carries political news structurally.
Excluded by the reporting standard without ever being named in UI: partisan opinion, commentary
programming, entertainment/celebrity, gambling, tobacco, recreational drugs, sexual content, culture-war
argument (never enumerated anywhere). Every catalog entry shows owner + country of origin. Kam AI does
not rate sources for bias and says so plainly; any added bias ratings must be attributed, not asserted,
with licensing verified.

## 10. Summarization rules (tight system instructions)
Never generate a headline (use publisher's own, unchanged). Attribute rather than assert. Describe,
do not evaluate (no commentary/conclusions/framing adjectives). Never merge multiple outlets into one
authoritative account — group same-story coverage and show all names. Rank by the reader's stated
interests, not by importance. Show source on every item. Front-page summaries 2-3 sentences. Be honest
when input was thin (say so, don't pad).

## 11. Coverage gaps
Computed locally from what the reader chose. Story in only one source -> say so on card + article view.
Several sources -> group + show count. Costs nothing, tells the reader about their own information diet.

## 12. Deliberately NOT
No infinite feed. No engagement mechanics/streaks/badges/unread counts. No push. No algorithmic
ranking beyond stated interests. No single merged AI voice. No behaviour-based recommendations.

## 13. Privacy requirements (non-negotiable)
Full article text fetched during overnight build, never on demand. No remote images during reading. No
cookies retained. No unique identifiers. Plain non-distinctive user agent. Tracking params stripped from
URLs before opening/storing. No third-party services in the path. Reading behaviour, keywords, saved
items, discussions, coverage analysis stored locally in the existing encrypted DB, never transmitted.
Must function normally through a system VPN. Update privacy policy + every echo (store listing, website,
onboarding, Q&A). Rewrite the current "no network requests unless user initiates" claim to cover the
scheduled edition, what publishers can/cannot infer, and the click-through exception. Update all together.

## 14. Testing
On device: full overnight build while charging (thermal + time-box); interrupted build resuming or
degrading to honest partial; build-now path with working cancel; read an edition fully offline (network
disabled) — must work completely; confirm with network monitoring that opening a story produces NO
request; discussion stays within article and offers both paths when it cannot answer; saving from article
and discussion then filtering to Today in Saved; add/remove sources incl. by site name; changing stories
per topic respected next edition; About Today at large font sizes and both themes. Regression tests: no
network request on article open; no remote image loading while reading; tracking params stripped;
summaries never replace publisher headlines; coverage gap computation; edition size honours the setting.
