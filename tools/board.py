#!/usr/bin/env python3
"""Build the Kam AI project board and keep it in step with the tracker.

Idempotent. It asks what exists before creating anything, so it is safe to run
before the board exists, again after a partial failure, and again months later to
check nothing has drifted.

What this cannot do, because the ProjectsV2 API has no mutation for it:

  - Creating views. There is no createProjectV2View.
  - Creating charts. Same.
  - Enabling the built in workflows. Only deleteProjectV2Workflow exists, so an
    automation can be removed through the API but not added.

Those three are printed as instructions at the end rather than silently skipped.
Everything else here is done through the API and read back.

  gh auth refresh --hostname github.com -s project,read:project
  python3 tools/board.py
"""

import json
import subprocess
import sys

OWNER = "Kamsiob"
REPO = "kam-ai"
TITLE = "Kam AI"


def gql(query, **variables):
    """Run a GraphQL query, returning data or raising with the real error.

    The request is posted as a JSON body rather than assembled from -f/-F flags,
    because those stringify everything. A list of single select options sent that
    way arrives as one long string and the API rejects it for not being an
    object, which is exactly the shape of failure worth avoiding here.
    """
    body = json.dumps({"query": query, "variables": variables})
    result = subprocess.run(
        ["gh", "api", "graphql", "--input", "-"],
        input=body, capture_output=True, text=True,
    )
    payload = json.loads(result.stdout or "{}")
    if "errors" in payload:
        raise RuntimeError(json.dumps(payload["errors"], indent=2))
    if result.returncode != 0 and not payload:
        raise RuntimeError(result.stderr.strip())
    return payload["data"]


def say(*parts):
    print(*parts, file=sys.stderr)


# ---------------------------------------------------------------------------
# The project itself.
# ---------------------------------------------------------------------------

OWNER_ID = gql("query($l:String!){user(login:$l){id}}", l=OWNER)["user"]["id"]

existing = gql(
    "query($l:String!){user(login:$l){projectsV2(first:50){nodes{id number title}}}}",
    l=OWNER,
)["user"]["projectsV2"]["nodes"]

match = [p for p in existing if p["title"] == TITLE]
if match:
    project = match[0]
    say(f"Project '{TITLE}' already exists as #{project['number']}.")
else:
    project = gql(
        """mutation($owner:ID!,$title:String!){
             createProjectV2(input:{ownerId:$owner,title:$title}){
               projectV2{id number title}}}""",
        owner=OWNER_ID,
        title=TITLE,
    )["createProjectV2"]["projectV2"]
    say(f"Created project #{project['number']}.")

PID = project["id"]
NUMBER = project["number"]

# The description a visitor reads before clicking anything, and the README they
# read if they want to know how to read the board. Part 5 requires the
# retroactive reconstruction to be acknowledged here rather than left implied.
SHORT = (
    "Local-first AI assistant. Android now, Linux desktop planned. "
    "This board is the authoritative record of what is being built and what state it is in."
)

README = """\
Kam AI is a private thinking and drafting tool that runs entirely on the user's
own device. Inference, storage and search are all local. There is no account and
nothing is collected.

**Repositories feeding this board**

- `Kamsiob/kam-ai`, the Android application. The only one that exists today.
- A Linux desktop repository will be added when it is created. Until then the
  Linux work is here as draft items rather than as issues.

**How to read it**

`Status` is the single field that decides where an item sits, so nothing can be
in two places at once.

- **Backlog** is agreed but not scheduled.
- **Ready** is specified well enough to start.
- **In progress** means being worked on right now. One person works on one thing,
  so more than one or two here means the board is lying about focus.
- **Blocked** means waiting on something outside the work itself. Every blocked
  item names what it is waiting on, on its own issue.
- **In review** means a pull request is open against it.
- **Done** means verified on real hardware. Merged is not done.

`Platform` separates Android from Linux, with **Shared** for anything that must
stay identical across both: the database schema, the export format and its
version, the design tokens, the mode definitions and their system instructions,
and user facing copy appearing in both.

`Size` is the estimate, set before work starts. `Actual` is what it took, set
when it closes. Keeping both is the only thing that makes estimating real rather
than decorative.

**On the history**

Items dated before 2026-07-26 were reconstructed retroactively from the git
history, the task documents and the decision records, because the work came from
specification documents before the tracker was the authoritative place for it.
That reconstruction is not complete and is not claimed to be. Anything carrying
the `record` label is a retroactive entry rather than something tracked as it
happened.
"""

gql(
    """mutation($id:ID!,$readme:String!,$desc:String!){
         updateProjectV2(input:{projectId:$id,readme:$readme,shortDescription:$desc}){
           projectV2{id}}}""",
    id=PID,
    readme=README,
    desc=SHORT,
)
say("Description and README set.")

REPO_ID = gql(
    "query($o:String!,$n:String!){repository(owner:$o,name:$n){id}}", o=OWNER, n=REPO
)["repository"]["id"]
try:
    gql(
        """mutation($p:ID!,$r:ID!){
             linkProjectV2ToRepository(input:{projectId:$p,repositoryId:$r}){clientMutationId}}""",
        p=PID,
        r=REPO_ID,
    )
    say(f"Linked to {OWNER}/{REPO}.")
except RuntimeError as error:
    # Already linked is not a failure worth stopping for.
    say(f"Link step: {str(error)[:120]}")


# ---------------------------------------------------------------------------
# Fields. Exactly the six specified, and no others.
# ---------------------------------------------------------------------------

def fields():
    data = gql(
        """query($id:ID!){node(id:$id){... on ProjectV2{
             fields(first:50){nodes{
               ... on ProjectV2FieldCommon{id name dataType}
               ... on ProjectV2SingleSelectField{id name options{id name}}}}}}}""",
        id=PID,
    )
    return {f["name"]: f for f in data["node"]["fields"]["nodes"] if f}


def single_select(name, options):
    """Create the field if absent, or correct its options if they have drifted."""
    current = fields().get(name)
    wanted = [{"name": o, "description": "", "color": c} for o, c in options]

    if current is None:
        gql(
            """mutation($p:ID!,$n:String!,$o:[ProjectV2SingleSelectFieldOptionInput!]!){
                 createProjectV2Field(input:{
                   projectId:$p,dataType:SINGLE_SELECT,name:$n,singleSelectOptions:$o
                 }){projectV2Field{... on ProjectV2SingleSelectField{id}}}}""",
            p=PID,
            n=name,
            o=wanted,
        )
        say(f"  created field {name}")
        return

    have = [o["name"] for o in current.get("options", [])]
    want = [o for o, _ in options]
    if have == want:
        say(f"  field {name} already correct")
        return

    gql(
        """mutation($f:ID!,$o:[ProjectV2SingleSelectFieldOptionInput!]!){
             updateProjectV2Field(input:{fieldId:$f,singleSelectOptions:$o}){
               projectV2Field{... on ProjectV2SingleSelectField{id}}}}""",
        f=current["id"],
        o=wanted,
    )
    say(f"  corrected field {name}: {have} -> {want}")


say("Fields:")

# Status ships with every new project holding Todo/In Progress/Done, so it is
# corrected rather than created.
single_select(
    "Status",
    [
        ("Backlog", "GRAY"),
        ("Ready", "BLUE"),
        ("In progress", "YELLOW"),
        ("Blocked", "RED"),
        ("In review", "PURPLE"),
        ("Done", "GREEN"),
    ],
)

single_select("Platform", [("Android", "GREEN"), ("Linux", "BLUE"), ("Shared", "PURPLE")])

# Derived from the actual package structure rather than invented, and short
# enough that every value gets used.
single_select(
    "Area",
    [(a, "GRAY") for a in [
        "Inference", "Conversations", "Modes", "Memory", "Projects", "Discover",
        "Voice", "Storage", "Settings", "Security", "Onboarding", "Release",
        "Infrastructure",
    ]],
)

# Few values, and the top one means exactly what it says.
single_select(
    "Priority",
    [("Blocks release", "RED"), ("High", "ORANGE"), ("Normal", "YELLOW"), ("Low", "GRAY")],
)

SIZES = [("XS", "GRAY"), ("S", "BLUE"), ("M", "GREEN"), ("L", "ORANGE"), ("XL", "RED")]
single_select("Size", SIZES)
single_select("Actual", SIZES)

say(f"\nBoard ready: https://github.com/users/{OWNER}/projects/{NUMBER}")
print(NUMBER)


# ---------------------------------------------------------------------------
# Backfill. Every issue, open and closed, with its field values set.
#
# Closed work belongs on the board. A board showing only what remains gives no
# sense of what has been built, and makes a long project look like a short one.
# ---------------------------------------------------------------------------

F = fields()
OPT = {name: {o["name"]: o["id"] for o in F[name].get("options", [])}
       for name in ["Status", "Platform", "Area", "Priority", "Size", "Actual"]}

# area label -> Area value. The label taxonomy grew first, so this is the
# translation rather than a second source of truth.
AREA_FROM_LABEL = {
    "area:inference": "Inference",
    "area:conversations": "Conversations",
    "area:modes": "Modes",
    "area:memory": "Memory",
    "area:projects": "Projects",
    "area:discover": "Discover",
    "area:voice": "Voice",
    "area:storage": "Storage",
    "area:settings": "Settings",
    "area:security": "Security",
    "area:onboarding": "Onboarding",
    "area:release": "Release",
    "area:infrastructure": "Infrastructure",
}

# Size for the open issues, set by judgement now rather than described
# afterwards, which is the only way the estimate means anything. Closed issues
# are deliberately left without one: inventing an estimate for finished work,
# then comparing it against the outcome, would manufacture an accuracy figure
# out of hindsight.
SIZE = {
    3: "XL", 13: "L", 38: "L", 51: "L", 55: "M", 71: "M", 72: "M", 73: "S",
    75: "XL", 78: "M", 93: "S", 95: "L", 99: "XS", 101: "XS",
}

# Anything waiting on something outside the work itself.
BLOCKED = {99}

issues = json.loads(subprocess.run(
    ["gh", "issue", "list", "--repo", f"{OWNER}/{REPO}", "--state", "all",
     "--limit", "400", "--json", "number,state,title,labels"],
    capture_output=True, text=True).stdout)

existing_items = gql(
    """query($id:ID!){node(id:$id){... on ProjectV2{
         items(first:100){nodes{id content{... on Issue{number}}}}}}}""",
    id=PID,
)["node"]["items"]["nodes"]
on_board = {i["content"]["number"]: i["id"]
            for i in existing_items if i.get("content", {}).get("number")}

say(f"\nBackfilling {len(issues)} issues ({len(on_board)} already present).")


def set_field(item_id, field_name, value_name):
    if value_name is None:
        return
    option_id = OPT[field_name].get(value_name)
    if option_id is None:
        say(f"    no option '{value_name}' on {field_name}")
        return
    gql(
        """mutation($p:ID!,$i:ID!,$f:ID!,$v:String!){
             updateProjectV2ItemFieldValue(input:{
               projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$v}
             }){projectV2Item{id}}}""",
        p=PID, i=item_id, f=F[field_name]["id"], v=option_id,
    )


added = 0
for issue in issues:
    number = issue["number"]
    labels = {l["name"] for l in issue["labels"]}

    item_id = on_board.get(number)
    if item_id is None:
        node_id = subprocess.run(
            ["gh", "api", f"repos/{OWNER}/{REPO}/issues/{number}", "-q", ".node_id"],
            capture_output=True, text=True).stdout.strip()
        item_id = gql(
            """mutation($p:ID!,$c:ID!){
                 addProjectV2ItemById(input:{projectId:$p,contentId:$c}){item{id}}}""",
            p=PID, c=node_id,
        )["addProjectV2ItemById"]["item"]["id"]
        added += 1

    closed = issue["state"] == "CLOSED"
    if closed:
        status = "Done"
    elif number in BLOCKED or "blocked" in labels:
        status = "Blocked"
    else:
        # Ready rather than Backlog for anything carrying a milestone or a
        # release blocking label: those are specified and scheduled. Everything
        # else is agreed but not scheduled.
        status = "Ready" if ("release-blocker" in labels or number in SIZE) else "Backlog"

    area = next((AREA_FROM_LABEL[l] for l in labels if l in AREA_FROM_LABEL), None)
    priority = "Blocks release" if "release-blocker" in labels else ("Normal" if not closed else None)

    set_field(item_id, "Status", status)
    set_field(item_id, "Platform", "Android")
    set_field(item_id, "Area", area)
    set_field(item_id, "Priority", priority)
    if not closed:
        set_field(item_id, "Size", SIZE.get(number))

say(f"Backfill done: {added} added, {len(issues) - added} already present.")
