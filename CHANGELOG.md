# Changelog

Notes are derived from the issues in each closed milestone rather than written
from memory, so what is listed is what was actually tracked and closed.

## 1.0.0

First public release.

An AI assistant that runs entirely on the phone. A model is downloaded once and
runs on the device after that, so nothing typed or generated is uploaded. No
account, no subscription, no ads, and nothing collected. It works in airplane
mode.

**Four ways of working.** General for ordinary questions. Logic Partner, which
argues the other side and tests reasoning rather than agreeing. Brainstorm, which
refuses to hand over ideas and pulls the user's own out instead. Workbench, for
reshaping text that is pasted in.

**Around them.** Projects that keep related chats under shared instructions.
Memory of durable things, all of it visible and deletable. One bookmark list for
anything worth returning to. Search across everything. Voice in and out, both on
the device. Discover, offline packs of short readings to pull one from and then
discuss. An assistant overlay on the power button.

**Underneath.** Encrypted local storage with the key wrapped by the Android
Keystore. An encrypted backup and restore file for moving between phones. A data
model that is already sync ready, though no sync exists and none is planned.

**The model it starts with.** First run offers the smallest model that actually
answers well rather than the smallest model. Measured on the device, the smaller
one answered a message about a bereavement by stating what it was, and the larger
one answered it properly. The download is longer, and everything that makes the
wait bearable is built: it runs in the background, resumes itself, says how long
is left, and the app is usable while it happens.

**The first message of a conversation.** The model's instructions and the first
thing a person types shared one turn with almost nothing between them, so the
first message of a conversation was read as the tail of an instruction sheet.
That is why first replies were sometimes strange in ways later ones never were.
They are now clearly separated.

**How the modes answer.** Logic Partner now says so plainly when an argument
holds, instead of returning nothing. Brainstorm picks a method and runs it
instead of offering a menu and asking which you would prefer. A statement gets a
reply that says something you did not say, rather than your own sentence back.
Short answers no longer arrive with a heading above them.

**Conversation titles.** A conversation can no longer be given a title about
something it never mentioned, and one that starts with a word or two takes its
name from the first message that actually says something.

**Replies that were really the instructions.** A reply that reproduces the app's
own instructions, one of its worked examples, or the message just typed is caught
before it is shown and generated again. Asked what model it ran on, it used to
answer with its own instruction sheet.

**Fixed before release.** Android limits how long an app may download in the
background each day. Reaching that limit used to kill the app mid-download, which
hit slow connections hardest, the exact case background downloading exists for.
The download now pauses cleanly, says so, and picks itself back up.

### Known limits, stated rather than discovered

The model is small enough to fit on a phone, so it knows less than the large ones
and will get obscure facts wrong. It cannot generate images. Without a connection
it knows nothing about recent events. Cold time to first token is around thirty
seconds on a Pixel 10 Pro XL, and subsequent turns in the same conversation are
far quicker because the cache is reused. On a device whose memory is already
heavily used, a large model may decline to load and say so rather than failing.
