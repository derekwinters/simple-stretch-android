# Notifications

- **NOTIF-001** A reminder notification's title is the stretch name(s); its expanded text lists
  each stretch with its duration and instructions. The schedule name is shown as the sub-text.
- **NOTIF-002** Tapping the notification opens the app.
- **NOTIF-003** A "Done" action dismisses the notification.
- **NOTIF-004** A "Skip rest of today" action skips today (SKIP-005).
- **NOTIF-005** On Android 13+ the app asks for `POST_NOTIFICATIONS` on first launch. While
  notifications are blocked, the home screen shows a banner linking to the app's notification
  settings.
- **NOTIF-006** An occurrence delivered more than 2 hours late (e.g. held by Doze) is not shown.
- **NOTIF-007** **Invariant — One occurrence produces at most one notification.** If the same
  occurrence is delivered twice (a reschedule re-armed it, SCHED-008), the second delivery is
  ignored.

## Build checklist

- [x] Channel created at app start with high importance.
- [x] All PendingIntents are `FLAG_IMMUTABLE`.
- [x] Receiver remembers the last-notified trigger per reminder (NOTIF-007).
- [ ] Robolectric test for notification content (not yet).
