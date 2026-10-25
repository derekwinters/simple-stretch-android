# Privacy policy

**Effective date: 25 October 2026**

Simple Stretch keeps everything you enter on your phone. It has no accounts, no analytics and
no ads, and it does not ask for internet permission. Nothing is collected by, or shared with, the
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

This data stays in the app's private storage on your phone. Android keeps it private to the
app; other apps on the phone do not have access to it. (Android's device backup, below, is a
separate copy.)

## What the app does not do

- It has no internet permission, so it does not send anything anywhere itself.
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

The app allows Android's own device backup and sets no limits on it, so all of the app's data
is included. If backup is turned on in your phone's settings, Android may copy all of the app's
data to your Google account and restore it later. When you move to a new phone, Android can
also copy it directly from the old phone to the new one. That backup and transfer are handled
by Android and Google under your Google account's settings, not by the developer, who has no
access to it.

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

The answers to give in Google Play Console's data safety form:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No (assumption below). |
| Is all of the user data collected by your app encrypted in transit? | Not shown: Play asks this only when the first answer is "Yes". |
| Do you provide a way for users to request that their data is deleted? | Not shown: Play asks this only when the first answer is "Yes". |
| Data types collected | None (assumption below). |
| Data types shared | None (assumption below). |

Basis: Play Console Help, "Provide information for Google Play's Data safety section"
(<https://support.google.com/googleplay/android-developer/answer/10787469>), defines "collect"
as transmitting data from your app off a user's device, and says user data processed only
locally on the device need not be disclosed. That page does not mention Android Auto Backup or
device-to-device transfer. The "No" and "None" answers therefore rest on an assumption: that
Android's own Auto Backup and device-to-device transfer, system services run by Android and
Google under the user's account rather than transmission by the app, are not "collection" by
the app. The page does not confirm this, and the owner must confirm it before entering these
answers in Play Console. If the assumption is wrong, the answers change: the first answer
becomes "Yes", the stored data types are listed as collected, and the deletion answer becomes
"Yes: clear the app's storage or uninstall".

<!--
Maintainer note: these answers are copied into Play Console by hand. They must change, in the
same pull request, whenever this policy or the app's permissions or network access change
(PRIV-004 in the privacy specification, docs/spec/privacy.md).
-->
