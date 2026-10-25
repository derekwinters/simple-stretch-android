# Privacy policy

**Effective date: 25 October 2026**

Simple Stretch keeps everything you enter on your phone. It has no accounts, no analytics and
no ads, and it cannot connect to the internet. Nothing is collected by, or shared with, the
developer.

## What the app stores

The app stores the following, on your device only:

- the stretches in your library (name, instructions and optional duration);
- your schedules and their reminder times (days of the week, set times or repeating windows,
  and which stretches each reminder prompts);
- your daily goals and their history (each goal count and the days it applied);
- the stretches you log, each with the time you logged it;
- the days you chose to skip;
- the scheduler's own settings: when each reminder alarm is next due and when the last reminder
  was shown, so a reminder is not shown twice.

This data stays in the app's private storage on your phone. Other apps cannot read it.

## What the app does not do

- It has no internet permission, so it cannot send anything anywhere.
- It has no accounts or sign-in.
- It contains no analytics, advertising, crash reporting or other third-party software
  development kits (SDKs).
- Nothing is collected by, or shared with, the developer or anyone else.

## Permissions

The app asks Android for three permissions, and uses them only for reminders:

- **Notifications**: to show your stretch reminders.
- **Alarms and reminders**: to show each reminder at the time you set. Without it, Android may
  deliver reminders a little late.
- **Run at startup**: to set your reminders again after the phone restarts.

## Device backup

The app allows Android's own device backup. If backup is turned on in your phone's settings,
Android may copy the app's data to your Google account and restore it to a new phone. That
backup is handled by Android and Google under your Google account's settings, not by the
developer, who has no access to it.

## Deleting your data

To delete everything the app has stored, clear its storage (in Android's settings, open Apps,
choose Stretch, then Storage, then Clear storage; the exact names vary between phones) or
uninstall the app. A copy in your Google account's device backup is managed in your Google
account.

## Children

The app is not directed at children under 13.

## Changes to this policy

Changes to this policy are made in the app's public repository, and the effective date above
changes with them. The history of every change is kept there.

## Contact

Questions or concerns: open an issue at
<https://github.com/derekwinters/simple-stretch-android/issues>.

## Play data safety form

The answers given in Google Play Console's data safety form:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No. |
| Is all of the user data collected by your app encrypted in transit? | Not applicable: the app collects no data and has no internet permission, so nothing is transmitted. |
| Do you provide a way for users to request that their data is deleted? | Not applicable: no data is collected. Data on the device is deleted by clearing the app's storage or uninstalling it. |
| Data types collected | None. |
| Data types shared | None. |

*Maintainer note:* these answers are copied into Play Console by hand. They must change, in
the same pull request, whenever this policy or the app's permissions or network access change
(PRIV-004 in the [privacy specification](spec/privacy.md)).
