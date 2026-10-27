<!-- store: omit -->

# Privacy

The app stores its data on the phone and never goes online, so it sends nothing anywhere itself.
The user-facing [privacy policy](../privacy.md) says so in plain words and records the answers
given in Google Play's data safety form; this page is the contract that keeps the policy true.

**Invariant — The policy states only what the app does.** A pull request that changes what can
leave the device (network access, analytics, crash reporting, any SDK that sends data) or the
declared permission set must update the [privacy policy](../privacy.md), including its "Play
data safety form" answers, in the same pull request.

- **PRIV-001** The app declares no `INTERNET` permission. Nothing the app stores can be sent
  anywhere by the app itself.
- **PRIV-002** App data is stored only on the device: the Room database (stretches, schedules
  and reminder times, daily goals and their history, logged stretches with their time, skipped
  dates) and the scheduler's local preferences (the trigger time of each armed alarm and of the
  last notified occurrence, SCHED-005, NOTIF-007). There are no accounts, analytics, ads, crash
  reporting or third-party SDKs that collect data.
- **PRIV-003** The declared permission set is exactly:
  - `POST_NOTIFICATIONS`: show stretch reminders (NOTIF-005);
  - `RECEIVE_BOOT_COMPLETED`: re-arm reminders after a restart;
  - `SCHEDULE_EXACT_ALARM`: fire reminders on time (SCHED-007).

  Adding or removing a permission is a change to this requirement and to the policy.
- **PRIV-004** The privacy policy page covers PRIV-001 to PRIV-003 in plain words, and also:
  that Android's device backup may copy all of the app's data to the user's Google account,
  and device-to-device transfer may copy it to a new phone, both handled by Android and Google
  rather than the developer; how to delete the data (clear the app's storage, or uninstall);
  that the app is not directed at children under 13; how the policy changes and how to get in
  touch; and the answers recorded for Play's data safety form. Because the backup wording says "all of the app's data", the manifest keeps
  `android:allowBackup="true"` on `<application>` with no `android:fullBackupContent` and no
  `android:dataExtractionRules`; `ManifestPermissionsTest` checks this.

## Build checklist

- [x] `ManifestPermissionsTest` reads the source manifest and asserts no `INTERNET` (PRIV-001),
      exactly the three permissions above (PRIV-003), and `allowBackup="true"` with no backup
      rules (PRIV-004).
- [x] The policy page lists what is stored, each permission, backup, deletion, children,
      changes and contact, and the data safety answers (PRIV-002, PRIV-004).
- [ ] The merged manifest is not checked: a library could in theory merge in a permission.
- [x] Store listing reviewed: this page is `store: omit`, and any claim in
      `docs/store/listing.md` that cites it is still true (STORE-LIST-002, STORE-LIST-003).
