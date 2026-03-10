# Closed testing rollout plan

This document captures the steps required to run the mandatory closed-track beta with real OPT students before production release.

## 1. Play Console steps

1. **Create the Closed testing track**
   - In Play Console, open **Testing → Closed testing → Create track**.
   - Name it “OPT pilot – Spring 2025” (or similar) to distinguish from the internal dogfood track.
2. **Upload the signed release bundle**
   - Reuse the release `.aab` that already passes internal QA.
   - Add concise release notes describing what you expect testers to focus on (employment logging, reporting reminders, vault stability).
3. **Enable Pre-launch report**
   - On the track’s release page toggle on the pre-launch report so Firebase Test Lab runs automated smoke tests on common devices.
4. **Add tester list**
   - Choose “Email list” and paste 15–30 addresses of real F‑1 / OPT students (university groups, alumni mailing lists, trusted friends).
   - Google now expects at least 12 active testers for 14 consecutive days before promoting to production—track enrolment daily.
5. **Publish the closed track**
   - After rollout, share the opt-in link with the curated list and remind them to accept it on the device they will use.

## 2. Tester onboarding script

Share this script (Google Doc/Notion) with each tester so feedback is structured:

1. Install the closed-track build from the Play Store link.
2. Create an account and finish onboarding:
   - Pick your OPT type.
   - Enter the OPT start date from your latest EAD.
3. Add employment:
   - Add your current job.
   - Add one past job with an end date.
4. Review the dashboard:
   - Confirm unemployment days and messaging make sense.
5. Trigger reporting + vault flows:
   - Use the job flow to create a reporting task.
   - Upload at least one document (EAD, offer letter, etc.).
6. Use it for a full week:
   - Make at least one change (edit a job or upload another document).
   - Note anything confusing or broken.
7. Submit feedback through the in-app “Send feedback” button after each significant action.

## 3. Success criteria

- ≥12 testers stay opted in and active for 14 days (meets Google Play requirement).
- Each tester completes the script, uploads at least one document, and sends feedback via the app.
- Crash-free users ≥ 99% and no blocker issues pending.

Log daily progress (installs, active testers, feedback summaries) in the product tracker so the production readiness review has hard data.
