# Launching Kam AI

Everything the build can do itself is automated. This file is the short list of
steps that must be done by a person, plus the one thing you must not lose.

## The one thing you must not lose: the upload keystore

The app is signed for the Play Store with an upload key at:

    ~/.kamsiob-secrets/kam-ai-upload.jks
    ~/.kamsiob-secrets/keystore.properties   (its password)

Back both up somewhere safe and private, off this machine. This is the *upload*
key, not the app signing key. Google Play App Signing holds the real signing key,
so if the upload key is ever lost it can be reset through Play support, but that
is a hassle you avoid by keeping a backup.

Upload key SHA-256 fingerprint (some steps ask for it):

    DC:91:1A:E7:0B:47:51:DC:69:2D:61:32:8C:B7:AD:4B:89:38:87:26:D6:FE:3D:42:F0:3A:38:72:F7:2A:E7:B4

## What is already done

- The app is built, signed, and passes its tests.
- The store listing text is written (`tools/play/listing.json`), in the app voice,
  honest about the limits, no "private ChatGPT" framing.
- The GitHub APK release is automated (`tools/cut_release.sh`).
- The Play listing and bundle upload are automated for later releases
  (`tools/play_publish.py`), using the service account already invited to the
  console.

## The manual steps, in order

These are the parts Google requires a person to do in the Play Console web UI. The
app entry for `com.kamsiob.kamai` already exists under the B7 Collective account.

1. **Upload the first build.** The very first bundle must go up through the web
   UI (the API cannot create the first release). Build it with:

       tools/cut_release.sh

   then in Play Console, Release, upload
   `app/build/outputs/bundle/release/app-release.aab` to the Internal testing
   track. After this first upload, later releases can use
   `tools/cut_release.sh --play`.

2. **Content rating (IARC questionnaire).** Play, Policy, App content, Content
   rating. Answer honestly: it is a text-based AI tool, no violence, no sexual
   content (it refuses both), user-generated content is the AI's replies which are
   reportable in-app. This will land it around Teen/PEGI 12 or similar.

3. **Target audience and content.** Set the target age to 18 and over, as decided.

4. **Ads declaration.** App content, Ads: declare that the app contains **no
   ads**.

5. **Data safety.** App content, Data safety. Declare **no data collected and no
   data shared**, consistent with the privacy policy: the app runs on-device and
   sends nothing about the user anywhere. (This form is not settable through the
   API, which is why it is here.)

6. **Privacy policy.** Store presence, Main store listing, paste the hosted URL:

       https://kamsiob.com/kam-ai-privacy.html

7. **Store listing.** The text is ready. Either paste it from
   `tools/play/listing.json`, or after the app has its first release run:

       python3 tools/play_publish.py update-listing

8. **Screenshots and graphics.** Add the phone screenshots and the feature
   graphic (see the README screenshots and `tools/make_store_assets.py`).

9. **Submit for review.** Once the above are green, the app is one button from
   review.

## The GitHub APK

Every release also publishes a plain APK on GitHub, for people who avoid the Play
Store or run a de-Googled phone. `tools/cut_release.sh` does this automatically and
verifies the published file against the built one. The release notes explain that
the GitHub APK and the Play build are signed differently, so switching between them
means uninstalling one first, and that conversations carry across with the app's
own Backup and restore.
