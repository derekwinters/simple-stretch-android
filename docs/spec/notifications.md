<!-- store: omit -->

# Notifications

- **NOTIF-001** A reminder notification's title is the stretch name(s); its expanded text lists
  each stretch with its duration and instructions. The schedule name is shown as the sub-text.
  A reminder with no stretches (every repeating schedule, SCHED-011) is titled "Time to
  stretch"; its text lists today's goals that are still short, as "Hamstring stretch 1 of 3",
  or (with no goals) shows the generic message. (Once every goal is met no reminder is shown at
  all, NOTIF-010.)
- **NOTIF-002** Tapping the notification opens the stretch session screen (SESS-001) and
  dismisses the notification. It carries the reminder's named stretches, its schedule name and
  its scheduled time into the session, which lists those stretches as their own section
  (SESS-002). A reminder with no stretches carries no named list.
- **NOTIF-003** A "Start" action does the same as tapping: it opens the stretch session screen
  with the same named stretches, schedule name and scheduled time, and dismisses the
  notification. (It replaces the earlier "Done" action.)
- **NOTIF-004** _Retired._ It described a "Skip today" notification action, which was removed.
  Skipping today is done from the home screen (SKIP-001) or the "Skipped days" screen
  (SKIP-002). A notification posted before the removal may still show the old button; tapping
  it does nothing (no skip is written and the notification is not dismissed), and the
  notification stays until swiped away or replaced.
- **NOTIF-005** On Android 13+ the app asks for `POST_NOTIFICATIONS` on first launch. While
  notifications are blocked, the home screen shows a banner linking to the app's notification
  settings.
- **NOTIF-006** An occurrence delivered more than 2 hours late (e.g. held by Doze) is not shown.
- **NOTIF-007** **Invariant — One occurrence produces at most one notification.** If the same
  occurrence is delivered twice (a reschedule re-armed it, SCHED-008), the second delivery is
  ignored.

- **NOTIF-008** A "Snooze 5 min" action dismisses the notification and posts the same reminder
  again 5 minutes later (SCHED-015). Snoozing again from the re-posted notification snoozes
  again. **Invariant — A snooze keeps the original scheduled time.** The re-posted reminder (and
  any snooze of it) carries the time it was first scheduled for, so the session heading says
  "From your 9:00 … reminder" even when it was re-posted at 9:05 (SESS-002).
- **NOTIF-009** Dismissing just this reminder is done by swiping the notification away (it is
  not ongoing). A reminder shows exactly two action buttons, Start and Snooze 5 min, in that
  order; the third slot Android allows is left empty.

  **Invariant — No notification button skips a day.** The reminder carries no skip action, and
  `NotificationActionReceiver` handles only Snooze; any other action it receives, including the
  old Skip today action, writes nothing and leaves the notification alone.
- **NOTIF-010** When at least one goal exists and every goal's progress today (GOAL-003) is at
  or above its count, a reminder is not shown. This applies to every reminder: set times (with
  or without stretches), repeating schedules and snoozed reminders. It is decided when the alarm
  is delivered, from the completion log at that moment, after the NOTIF-007 duplicate check and
  the enabled, day, skip and staleness checks. With no goals, reminders are shown as before.

  **Invariant — Met goals suppress the notification, never the schedule.** The next alarm is
  still armed exactly as if the reminder had been shown (SCHED-006), so logging one more
  stretch, or a new day starting, never needs a reschedule for reminders to resume. With no
  goals defined, goals never suppress anything.

## Build checklist

- [x] Channel created at app start with high importance.
- [x] All PendingIntents are `FLAG_IMMUTABLE`.
- [x] Receiver remembers the last-notified trigger per reminder (NOTIF-007).
- [x] Start and the content tap share one activity PendingIntent carrying the notification id;
      `MainActivity` cancels that notification and navigates to the session (NOTIF-002/003).
- [x] Snooze is a broadcast action handled by `NotificationActionReceiver` (NOTIF-008).
- [x] The scheduled time travels notification → snooze alarm → re-posted notification as an
      extra; only a missing extra (an alarm armed before this) falls back to the trigger time
      (NOTIF-008 invariant).
- [x] The reminder's buttons come from one pure list, unit tested as exactly Start then Snooze;
      the receiver's handled actions are a pure set, unit tested to exclude the old Skip today
      action (NOTIF-004 retired, NOTIF-009).
- [x] "Every goal met" is the pure `GoalMath.allGoalsMet` (false for no goals), unit tested
      (NOTIF-010).
- [x] The receiver returns before posting, but `onAlarmHandled` still runs, so the next alarm
      is armed (NOTIF-010 invariant).
- [ ] Robolectric test for notification content (not yet).
- [x] Store listing reviewed: this page is `store: omit`, and any claim in
      `docs/store/listing.md` that cites it is still true (STORE-LIST-002, STORE-LIST-003).
