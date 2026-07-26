# Continuous integration

`ci.yml` in this directory is the workflow, complete and ready. It is here rather
than in `.github/workflows/` because the CLI token this repository is maintained
with does not carry the `workflow` scope, and GitHub refuses a push that creates or
changes a workflow file without it:

```
! [remote rejected] main -> main (refusing to allow an OAuth App to create or
  update workflow `.github/workflows/ci.yml` without `workflow` scope)
```

## To activate it

```sh
gh auth refresh -s workflow
git mv docs/ci/ci.yml .github/workflows/ci.yml
git commit -m "ci: activate the workflow" && git push
```

Then delete this file, since it stops being true.

## What it does

Two jobs. The first compiles the app, compiles **both** test source sets, runs the
unit suite, and runs Android lint. The second assembles the app, which compiles
llama.cpp and whisper.cpp from source and is slow enough to deserve its own job.

Compiling the instrumented tests explicitly is the point of the first job as much
as the unit tests are. They are excluded from the default build, which is exactly
how they were once allowed to rot unnoticed, and no check that only builds the app
would have caught it.

Lint is gated at zero errors, which it currently passes. Warnings are not gated:
there are fifty, and gating them all at once would mean either a permanently red
badge or a blanket suppression, both of which are worse than a count that is
visible in the log.
