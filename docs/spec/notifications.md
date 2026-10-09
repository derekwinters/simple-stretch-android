# Notifications

- **NOTIF-001** A reminder notification's title is the stretch name(s); its expanded text lists
  each stretch with its duration and instructions. The schedule name is shown as the sub-text.
  A reminder with no stretches (every repeating schedule, SCHED-011) is titled "Time to
  stretch"; its text lists today's goals that are still short, as "Hamstring stretch 1 of 3",
  or (with no goals) shows the generic message. (Once every goal is met no reminder is shown at
  all, NOTIF-010.)
- **NOTIF-002** Tapping the notification opens the stretch session screen (SESS-001) and
  dismisses the notification.
- **NOTIF-003** A "Start" action does the same as tapping: it opens the stretch session screen
  and dismisses the notification. (It replaces the earlier "Done" action.)
- **NOTIF-004** A "Skip today" action skips today (SKIP-005).
- **NOTIF-005** On Android 13+ the app asks for `POST_NOTIFICATIONS` on first launch. While
  notifications are blocked, the home screen shows a banner linking to the app's notification
  settings.
- **NOTIF-006** An occurrence delivered more than 2 hours late (e.g. held by Doze) is not shown.
- **NOTIF-007** **Invariant — One occurrence produces at most one notification.** If the same
  occurrence is delivered twice (a reschedule re-armed it, SCHED-008), the second delivery is
  ignored.

- **NOTIF-008** A "Snooze 5 min" action dismisses the notification and posts the same reminder
  again 5 minutes later (SCHED-015). Snoozing again from the re-posted notification snoozes
  again.
- **NOTIF-009** Dismissing just this reminder is done by swiping the notification away (it is
  not ongoing). Android shows at most three action buttons, so the three buttons are Start,
  Snooze 5 min and Skip today, in that order.
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
- [x] "Every goal met" is the pure `GoalMath.allGoalsMet` (false for no goals), unit tested
      (NOTIF-010).
- [x] The receiver returns before posting, but `onAlarmHandled` still runs, so the next alarm
      is armed (NOTIF-010 invariant).
- [ ] Robolectric test for notification content (not yet).
