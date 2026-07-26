#!/usr/bin/env bash
# Build the Kam AI project board, and backfill every issue onto it.
#
# Idempotent on purpose. It is written to be run before anybody knows whether
# the board exists, re-run after a partial failure, and re-run months later to
# check that nothing has drifted. Every step asks what is there before it makes
# anything, so a second run is a no-op rather than a duplicate.
#
# Needs a token with 'project' and 'read:project'. Without them the very first
# read fails, which is the correct place to fail: making half a board is worse
# than making none.
#
#   gh auth refresh -s project,read:project
#   tools/setup_board.sh
#
set -euo pipefail

OWNER=Kamsiob
REPO=kam-ai
TITLE="Kam AI"

say() { printf '%s\n' "$*" >&2; }

# ---------------------------------------------------------------------------
# Scope check, up front and by name, so the failure is legible.
# ---------------------------------------------------------------------------
if ! gh project list --owner "$OWNER" --format json >/dev/null 2>&1; then
  say "Cannot read projects. The token is missing 'project' and 'read:project'."
  say "Run: gh auth refresh -s project,read:project"
  exit 1
fi

# ---------------------------------------------------------------------------
# The project itself.
# ---------------------------------------------------------------------------
NUM=$(gh project list --owner "$OWNER" --format json \
  | python3 -c "
import json,sys
ps=json.load(sys.stdin)['projects']
m=[p for p in ps if p['title']=='$TITLE']
print(m[0]['number'] if m else '')
")

if [ -z "$NUM" ]; then
  say "Creating project '$TITLE'."
  NUM=$(gh project create --owner "$OWNER" --title "$TITLE" --format json \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["number"])')
else
  say "Project '$TITLE' already exists as #$NUM."
fi

PID=$(gh project view "$NUM" --owner "$OWNER" --format json \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

gh project edit "$NUM" --owner "$OWNER" \
  --readme "$(cat <<'EOF'
Kam AI is a local-first AI assistant for Android. Everything runs on the phone.

**Status** is the only field that decides where an item sits. Nothing lives in
two places, and nothing is tracked outside this board.

- **Blocked** means waiting on something outside the work itself. The blocker is
  named in a comment on the issue, never only here.
- **In progress** means one person, right now. More than three at once is a
  signal that something is stalled, not that a lot is happening.
- **Needs verification** means the code is merged and the behaviour has not yet
  been confirmed on a real device. Nothing skips this.
- **Done** requires that verification. Merged is not done.
EOF
)" >/dev/null
say "README set."

# ---------------------------------------------------------------------------
# Fields.
#
# Status comes with every project and already holds Todo/In Progress/Done, so it
# is extended rather than replaced: rebuilding it would orphan any item already
# sitting in it.
# ---------------------------------------------------------------------------
fields_json() { gh project field-list "$NUM" --owner "$OWNER" --format json; }

field_id() {
  fields_json | python3 -c "
import json,sys
for f in json.load(sys.stdin)['fields']:
    if f['name']=='$1': print(f['id']); break
"
}

has_field() { [ -n "$(field_id "$1")" ]; }

# A single-select field with its options, created only if absent.
make_select() {
  local name="$1"; shift
  if has_field "$name"; then say "Field '$name' present."; return; fi
  say "Creating field '$name'."
  local args=()
  for opt in "$@"; do args+=(--single-select-option "$opt"); done
  gh project field-create "$NUM" --owner "$OWNER" \
    --name "$name" --data-type SINGLE_SELECT "${args[@]}" >/dev/null
}

# The six fields the specification names: Status, Platform, Area, Priority,
# Size, Actual. Recovered from issue #99, which recorded them at the time the
# instruction was given. An earlier draft of this script invented a different
# set (Area, Size, Risk, Verified on device) and would have built the wrong
# board convincingly, which is worse than building none.

make_select "Platform" "Android" "Linux" "Shared"

make_select "Area" \
  "Inference" "Chat" "Projects" "Discover" "Voice" "Storage" "Settings" \
  "Onboarding" "Design system" "Build and release" "Docs"

make_select "Priority" "P0" "P1" "P2" "P3"

# Size is the estimate. Actual is what it took. Two fields rather than one
# because overwriting the estimate with the outcome destroys the only evidence
# of whether estimates are any good.
make_select "Size" "XS" "S" "M" "L" "XL"
make_select "Actual" "XS" "S" "M" "L" "XL"

# Status: add the three states a bare project lacks. GraphQL, because the CLI
# cannot edit an existing single-select's options.
STATUS_ID=$(field_id "Status")
CURRENT_STATUS=$(fields_json | python3 -c "
import json,sys
for f in json.load(sys.stdin)['fields']:
    if f['name']=='Status':
        print(','.join(o['name'] if isinstance(o,dict) else o for o in f.get('options',[])))
")
say "Status options are: ${CURRENT_STATUS:-none}"

WANT_STATUS='Todo,In progress,Blocked,Needs verification,Done'
if [ "$CURRENT_STATUS" != "$WANT_STATUS" ]; then
  say "Setting Status options to: $WANT_STATUS"
  gh api graphql -f query='
    mutation($field:ID!, $opts:[ProjectV2SingleSelectFieldOptionInput!]!) {
      updateProjectV2Field(input:{
        fieldId:$field
        singleSelectOptions:$opts
      }) { projectV2Field { ... on ProjectV2SingleSelectField { id } } }
    }' \
    -f field="$STATUS_ID" \
    -f opts='[
      {name:"Todo",color:GRAY,description:"Agreed and not started"},
      {name:"In progress",color:YELLOW,description:"Being worked on now"},
      {name:"Blocked",color:RED,description:"Waiting on something named in a comment"},
      {name:"Needs verification",color:BLUE,description:"Merged, not yet confirmed on a device"},
      {name:"Done",color:GREEN,description:"Verified on a real device"}
    ]' >/dev/null || say "Status option update failed; set them by hand in the UI."
fi

# ---------------------------------------------------------------------------
# Backfill. Every issue, open and closed, so the board is the whole history
# rather than a snapshot of what is left.
# ---------------------------------------------------------------------------
say "Backfilling issues."
gh issue list --repo "$OWNER/$REPO" --state all --limit 300 \
  --json number,state,title,labels,closedAt > /tmp/kamai_issues.json

python3 - "$NUM" "$OWNER" "$PID" <<'PY'
import json, subprocess, sys

num, owner, pid = sys.argv[1], sys.argv[2], sys.argv[3]
issues = json.load(open('/tmp/kamai_issues.json'))

def sh(*a):
    return subprocess.run(a, capture_output=True, text=True)

# What is already on the board, so a re-run adds nothing twice.
out = sh('gh', 'project', 'item-list', num, '--owner', owner,
         '--format', 'json', '--limit', '400').stdout
on_board = set()
if out.strip():
    for it in json.loads(out).get('items', []):
        c = it.get('content') or {}
        if c.get('number'):
            on_board.add(c['number'])

added = 0
for i in issues:
    n = i['number']
    if n in on_board:
        continue
    r = sh('gh', 'project', 'item-add', num, '--owner', owner,
           '--url', f'https://github.com/{owner}/kam-ai/issues/{n}')
    if r.returncode == 0:
        added += 1
    else:
        print(f'  #{n} failed: {r.stderr.strip()}', file=sys.stderr)

print(f'{added} added, {len(on_board)} already present.', file=sys.stderr)
PY

say ""
say "Board ready: https://github.com/users/$OWNER/projects/$NUM"
say ""
say "Two things the API cannot do, so they need a click each:"
say "  1. Views. Create 'Board' (group by Status), 'By area' (group by Area),"
say "     and 'Roadmap' (a roadmap view over the Verified on device field)."
say "     The ProjectV2 API has no view-creation mutation."
say "  2. Built-in automation. Project settings > Workflows > enable"
say "     'Item closed' -> Needs verification, and 'Auto-add to project'."
say "     Workflow enablement is UI-only."
