# Google Play internal testing checklist

1. **Prepare console access**
   - Enroll in the Google Play Developer Program (pay the one-time fee and complete identity verification).
   - Create the OPT Pal application entry in the Play Console and fill in the core metadata (title, short description, disclosures).

2. **Create a signed release bundle**
   - In Android Studio run *Build > Generate Signed Bundle/APK* and choose `Android App Bundle`.
   - Use your release keystore and remember to keep the keystore + passwords in a secure location.
   - Verify the generated `.aab` by installing it locally via `bundletool` if required.

3. **Set up an internal testing track**
   - In Play Console open **Testing → Internal testing** and create a new release.
   - Upload the signed `.aab`, add release notes, and assign at least one tester (email list or Google Group).
   - After publishing, copy the opt-in link and share it with your small tester cohort.

4. **Install and verify**
   - Each tester accepts the opt-in link, waits for propagation (~15 minutes), and installs the app from the Play Store.
   - Capture screenshots/logs for any ANRs or crashes and feed them back into the issue tracker.

5. **Monitor vitals**
   - Enable Play Console crash / ANR alerts for the internal track.
   - Confirm Crashlytics is receiving events (open Firebase console → Crashlytics).
   - Review analytics dashboards for the screen-view and action events that were added (Dashboard, Add Employment, Reporting Hub, Document Vault, Employment saved, etc.).

6. **Promote when stable**
   - Once internal testing is stable, copy the release to a closed or open testing track for a larger audience, keeping the same signed bundle and versioning.
