# Security

Kam AI keeps conversations on the device and encrypts them at rest. That makes a
vulnerability here a privacy problem rather than only a reliability one, so please
report one privately rather than opening a public issue.

## Reporting

Use GitHub's private vulnerability reporting on this repository: the **Security**
tab, then **Report a vulnerability**. It opens a private thread visible only to
the maintainer.

If that is unavailable to you, email **karimabaz@gmail.com** with `SECURITY` in
the subject.

Please include what you found, how to reproduce it, and what you think the impact
is. A proof of concept helps and is not required.

## What to expect

This project is maintained by one person, so response times vary. Honestly:

- An acknowledgement within a week.
- An assessment of whether it is exploitable, and how, once I have looked
  properly.
- A fix in a release, with the issue made public afterwards and credit if you
  want it.

If I disagree that something is a vulnerability I will say so and explain why,
rather than letting the report go quiet.

## Scope

In scope: anything that exposes conversation content, memory, or the database key
to another app, another user of the device, or the network. Anything that defeats
the app lock. Anything that sends data off the device that the app claims stays on
it.

Out of scope: an attacker who already has a root shell or the unlocked device.
That is not a threat this app claims to defend against, and pretending otherwise
would be the kind of overclaim the rest of the project avoids.

## What the app actually promises

Worth stating plainly so a report can be judged against it. The database is
SQLCipher-encrypted with a key wrapped by the Android Keystore, in StrongBox where
the device has it. Conversation KV caches on disk are encrypted with a per-file
data key that the Keystore wraps. Inference is local: the app touches the network
only to download a model, a voice, or a content pack, or if the user turns on web
search themselves.
