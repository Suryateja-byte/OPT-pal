# Closed test metric framework

Use Firebase Analytics + Crashlytics to watch these metrics during the 14‑day closed test.

## Activation funnel

| Metric | Source | Notes |
| --- | --- | --- |
| Profile setup completion rate | `setup_completed` event (`opt_type` param) | Fired when the user saves OPT type/date in `SetupViewModel`. |
| First employment added | `employment_saved` event (`employer_name` param) | Already logged from `AddEmploymentViewModel.saveEmployment`. |
| Dashboard revisit | `screen_view` with `screen_name=Dashboard` | Track consecutive-day visits to approximate D1/D7/D14 retention. |

## Feature adoption

| Metric | Source | Notes |
| --- | --- | --- |
| Document uploads per tester | `document_uploaded` event (`document_tag`) | Triggered in `DocumentVaultViewModel`. |
| Document deletions | `document_deleted` event | Indicates trust with vault. |
| Reporting actions | `reporting_completed` event (`obligation_id`) | Logged when a pending task is checked off. |
| Feedback submissions | `feedback_submitted` event (`rating`) | New in-app feedback form logs this for every send. |

## Retention + engagement

* Use `screen_view` with `screen_name` = `Dashboard`, `Reporting`, `DocumentVault`, `Feedback`, `Legal` to understand weekly reach of each area.
* Define “activated user” as someone who produced both `setup_completed` and `employment_saved` events; filter D1/D7/D14 retention on that cohort.

## Stability & trust

* Crash-free users / sessions — monitor in Crashlytics daily.
* Pre-launch report on the closed track stays enabled to catch device-specific issues before testers download updates.
* Feedback intensity — daily count of `feedback_submitted` events + content from Firestore `feedback` collection; spikes signal UX pain.

Review these metrics every 2–3 days during the closed test and copy summary stats into the pilot log (installs, activation %, document adoption, crash-free %) before deciding on production rollout.
