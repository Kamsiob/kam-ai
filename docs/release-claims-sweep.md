# The network and data claims sweep

Three false statements about network behavior were found in three days: an upload
queue that does not exist, a web search that is not in the build, and a privacy
policy promising no network requests. That is a pattern, not three incidents, and it
is the one category where being wrong is unrecoverable, because a screenshot of the
application making a false privacy claim outlives the correction.

This is the checklist. Run it before any release, and after any change that touches
networking or removes a feature.

## Ground truth: what the application actually does

Derived by finding every call site of the HTTP client, not by reading feature
descriptions. **There are exactly two network calls.**

1. **Downloads.** Models, voices and content packs, each started by the user
   tapping a button that names the size. `Downloader`.
2. **The content pack list.** Fetched when the Discover screen is opened.
   `KamRepository.fetchDiscoverManifest`. Sends nothing about the user, fails
   silently to an empty list offline.

**Not present:** telemetry, analytics, crash reporting, update checks, prefetching,
background connections, advertising ID, web search.

To re-derive: `grep -rn "newCall(" app/src/main/java`. Two files should appear. If a
third does, the ground truth has changed and every statement below must be
re-checked.

## Every place a claim can live

Check each against the two calls above. Where two sources disagree, one is wrong,
and which one is the interesting question.

**Check the highest risk location first, because nothing downstream can catch an
error in it.** Three of the four false claims found so far were in prose, where a
wrong sentence sits there being read. The fourth was in the one structurally exempt
place: a sentence the model was *instructed* to say, sitting on the echo guard's
exemption list, bypassing every check by design, answering the question a privacy
conscious user asks. So this sweep covers the guard's own allowances and not only
documentation.

- [ ] **`PromptEcho.ALWAYS_ALLOWED`, the exemption list. HIGHEST RISK.** Every entry
      is a factual claim about the application that bypasses every check in the
      guard. Read via `PromptEcho.exemptAnswers`. For each entry:
      - Is it verified against what the application actually *does*, not against what
        was believed when it was added? "It is true wherever it lands" is a claim
        needing evidence, not a reason. That exact phrase justified two entries and
        was wrong about one.
      - Has anything it describes changed since it was verified? An entry must be
        re-verified when its subject changes, not only when it is edited.
      - Is it verifiable at all? **If not, it comes off.** An exemption nobody can
        check is worse than none: a rejected right answer costs one fallback message,
        an unchecked wrong claim ships.
      - Test the *breadth*, not the sentence. Ask what questions it could land on, not
        the one it was written for. "Everything works the same offline" was true of
        what the user types and false of downloading a model or opening Discover.
- [ ] **`SystemPrompts.HARD_RULES`, the example answers.** The exemption list's
      entries are quoted here, which is what makes the model produce them reliably.
      A claim fixed in one place and not the other leaves the guard and the prompt
      disagreeing, and `ALWAYS_ALLOWED` matches by prefix, so they must be the same
      text.

- [ ] `tools/play/listing.json`, full description
- [ ] `docs/release-data-safety.md`, the declaration itself
- [ ] `PRIVACY.md`
- [ ] The hosted privacy copy, if it diverges from `PRIVACY.md`
- [ ] `README.md`
- [ ] `docs/index.md`, the website
- [ ] **`OnboardingCopy.kt`, every slide. Higher risk than its position suggests.** It is the
      first thing a user reads and the last thing a documentation sweep looks at, because it is
      a string in the codebase rather than a document. The original sweep removed the web search
      claim from the documentation and **onboarding went on promising it** ("unless you add
      search") until a separate audit found it. Check first-run copy against the build, not
      against the documents, since the documents can already be correct while the app is not.
- [ ] `QuestionsAndAnswers.kt`, every entry
- [ ] Every settings row title and subtitle, `SettingsScreens.kt`
- [ ] Every empty state, including Discover's and Chats'
- [ ] The model picker and the download screens
- [ ] The storage screen
- [ ] **The model's own answers**, which is the one that is not a string in the
      codebase and therefore the easiest to skip

## The model's answers

Not greppable, and the source of the worst of the three. Ask directly, in a fresh
conversation, in **every mode** and on **both tiers**:

- Does this use the internet?
- Where does my data go?
- Is anything I type uploaded?
- Does it work offline?
- Is there a server?
- What happens to what I type when I have no signal?

Every wrong answer is the same defect as the two found in prose and blocks release.
`tools/session.sh` held for several turns as somebody who does not trust the
application is what found the upload-queue answer.

Two worked examples now cover the two questions that failed, and both are exempt
from every guard check because both are true wherever they land. A third failing
question wants the same treatment: an example, in the shared hard rules, exempt.

## What this sweep has caught

| where | claim | reality |
|---|---|---|
| the model's answer | text "stays on this phone until you have a connection" | no queue exists (#136) |
| the model's answer | "are you using the native application or a web browser version?" | no web version (#135) |
| store listing | the internet is used "for web search if you switch that on" | not in the build |
| in-app questions | "or if you set up web search yourself" | not in the build |
| `PRIVACY.md` | web search, as a bullet | not in the build |
| `PRIVACY.md` | "makes no network requests" if you never download | Discover fetches the pack list |
| `README.md` | "no transport, no server and no network code" | true of sync only |

All three prose failures were written before or alongside a feature and drifted
silently. None were in code. **Documentation does not fail loudly, so it has to be
checked against the build rather than against the last version of itself.**
