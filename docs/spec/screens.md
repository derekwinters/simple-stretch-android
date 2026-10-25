# Screens

- **HOME-001** The home screen shows today's date at the top. There is no skip card; skipping
  today is done from the Today row (HOME-002) or the overflow menu (HOME-004).
- **HOME-002** Below it, today's reminders from all enabled schedules, in time order, with the
  time, stretches and schedule name. A repeating schedule shows as one row at its next slot
  today (or its last slot, dimmed, once all have passed) with its repeat summary and how many
  slots are left today. Reminders already past, or all of them when today is skipped, are
  dimmed. The "Today" heading row carries a text button at its right end (SKIP-001): "Skip
  today" while today is not skipped (enabled when at least one reminder is left today, shown
  disabled when none are), and "Resume today" once today is skipped. Every row opens the
  stretch session when tapped (HOME-009).

  **Invariant — When today is skipped, the Today row always shows "Resume today".** No state
  hides it: not an empty Today list, not the time of day, and nothing stored per device.
- **HOME-003** Below that, every schedule with its days, its times (or repeat summary, e.g.
  "Every 60 min at :50, 8:00 AM - 5:00 PM") and an enabled switch. Tapping a schedule opens its
  editor; a button creates a new one.
- **HOME-004** The top bar has at most two icon actions plus an overflow menu. The goals screen
  is an icon ("Daily goals"); the overflow menu holds labelled text items "Skip today" (or
  "Resume today"), "Skip other days" (the skipped-days screen), "My stretches" (the stretch
  library, LIB-006) and "Trends" (TREND-004).

  **Invariant — The stretch library is reached by a labelled item**, never only by an
  unlabelled icon.
- **HOME-005** Banners appear when notifications are blocked (NOTIF-005) or exact alarms are not
  allowed (SCHED-007).

  **Invariant — Toggling a schedule's switch on the home screen reschedules immediately**; there
  is no separate save step for it.

- **HOME-006** A "Today's goals" card, below the date, shows each goal's progress today
  (e.g. "Hamstring stretch 2/3" with a progress bar) and links to the goals screen and, in
  its header while goals exist, to the Trends screen (TREND-009). With no goals it invites the
  user to set one.
- **HOME-007** A "Stretch now" button on the goals card opens the stretch session screen
  (SESS-001) without waiting for a reminder.
- **HOME-008** _Retired._ It described the closable "Skip today" card, which was removed with
  its close (X) button and its stored closed date. Its invariant, a skipped day is never hidden,
  now lives in HOME-002.
- **HOME-009** Tapping any row of the Today list (HOME-002) opens the stretch session screen
  (SESS-001) as that reminder's notification would (NOTIF-002). Every row is tappable: past,
  upcoming and current alike, and also while today is skipped. A set-time row opens the
  session with its reminder's named stretches, its schedule name and the row's time today; a
  repeating schedule's row, whose reminders name no stretches (SCHED-011), opens it with no
  named list ("Goals still short today"), as its notification does. Tapping only opens the
  screen: nothing is checked or logged until Save (SESS-002, SESS-003), and rows look the same
  whether or not their stretches were logged.

  **Invariant — A Today row opens exactly what its notification would.** The row and the
  notification build the session route with the same function from the same values (stretch
  ids in the reminder's order, schedule name, scheduled time), so the two ways in cannot drift
  apart.

## Build checklist

- [x] Home state is derived from Room flows plus a 30-second tick that reads the app clock
      (HOME-002, SCHED-016).
- [x] Today rows' past/dimmed state is unit tested with a fixed clock (HOME-002).
- [x] Banners re-check permissions on resume (HOME-005).
- [x] The exact-alarm banner is the only place the user is asked for exact alarms: no prompt at
      first launch or on first schedule save (HOME-005, SCHED-007).
- [x] Today row: a "Skip today" / "Resume today" text button at the right of the heading,
      disabled when nothing is left today; its state is a pure, unit-tested function (HOME-002).
- [x] No skip-card closed date is written or read (HOME-008 retired).
- [x] Top bar: one icon (goals) + overflow with Skip today, Skip other days, My stretches,
      Trends (HOME-004).
- [x] Goal progress re-queries when the date changes (the clock tick drives the day bounds)
      (HOME-006).
- [x] Today rows build their session route with the same pure, unit-tested function as the
      notification intent, from the reminder's stretch ids, schedule name and today's time
      (HOME-009 invariant).
