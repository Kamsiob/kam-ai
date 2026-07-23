#!/usr/bin/env python3
"""Android Publisher API helper for Kam AI.

Manages what the API can manage: uploading a bundle to a track, and setting the
store listing text. It cannot set the Data Safety form or the content rating;
those are one-time manual steps in the Play Console, listed in LAUNCH.md.

The service account key lives outside the repo at
~/.kamsiob-secrets/play-service-account.json (kamsiob@kamsiob-503213.iam...),
invited with rights in the B7 Collective Play Console.

  python3 tools/play_publish.py upload-bundle --aab path/to/app-release.aab [--track internal]
  python3 tools/play_publish.py update-listing [--listing tools/play/listing.json]
"""

import argparse
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.kamsiob.kamai"
KEY = os.path.expanduser("~/.kamsiob-secrets/play-service-account.json")
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
HERE = os.path.dirname(os.path.abspath(__file__))


def service():
    if not os.path.exists(KEY):
        sys.exit(f"Service account key not found at {KEY}")
    creds = service_account.Credentials.from_service_account_file(KEY, scopes=SCOPES)
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def upload_bundle(aab, track):
    api = service()
    edit = api.edits().insert(packageName=PACKAGE, body={}).execute()
    eid = edit["id"]
    up = api.edits().bundles().upload(
        packageName=PACKAGE, editId=eid,
        media_body=MediaFileUpload(aab, mimetype="application/octet-stream", resumable=True),
    ).execute()
    version_code = up["versionCode"]
    api.edits().tracks().update(
        packageName=PACKAGE, editId=eid, track=track,
        body={"releases": [{"versionCodes": [str(version_code)], "status": "completed"}]},
    ).execute()
    api.edits().commit(packageName=PACKAGE, editId=eid).execute()
    print(f"Uploaded version code {version_code} to the {track} track.")


def update_listing(listing_path):
    with open(listing_path) as f:
        listing = json.load(f)
    api = service()
    edit = api.edits().insert(packageName=PACKAGE, body={}).execute()
    eid = edit["id"]
    api.edits().listings().update(
        packageName=PACKAGE, editId=eid, language=listing.get("language", "en-US"),
        body={
            "title": listing["title"],
            "shortDescription": listing["shortDescription"],
            "fullDescription": listing["fullDescription"],
        },
    ).execute()
    api.edits().commit(packageName=PACKAGE, editId=eid).execute()
    print("Store listing updated.")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("upload-bundle")
    b.add_argument("--aab", required=True)
    b.add_argument("--track", default="internal")
    l = sub.add_parser("update-listing")
    l.add_argument("--listing", default=os.path.join(HERE, "play", "listing.json"))
    args = ap.parse_args()

    if args.cmd == "upload-bundle":
        upload_bundle(args.aab, args.track)
    elif args.cmd == "update-listing":
        update_listing(args.listing)


if __name__ == "__main__":
    main()
