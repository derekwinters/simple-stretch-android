# Screens

- **HOME-001** The home screen shows today's date and the "Skip today" card (SKIP-001) at the top
  (unless closed for today, HOME-008).
- **HOME-002** Below it, today's reminders from all enabled schedules, in time order, with the
  time, stretches and schedule name. A repeating schedule shows as one row at its next slot
  today (or its last slot, dimmed, once all have passed) with its repeat summary and how many
  slots are left today. Reminders already past, or all of them when today is skipped, are
  dimmed.
- **HOME-003** Below that, every schedule with its days, its times (or repeat summary, e.g.
  "Every 60 min at :50, 8:00 AM - 5:00 PM") and an enabled switch. Tapping a schedule opens its
  editor; a button creates a new one.
- **HOME-004** The top bar links to the goals screen, the stretch library and the
  skipped-days screen.
- **HOME-005** Banners appear when notifications are blocked (NOTIF-005) or exact alarms are not
  allowed (SCHED-007).

  **Invariant — Toggling a schedule's switch on the home screen reschedules immediately**; there
  is no separate save step for it.

- **HOME-006** A "Today's goals" card, below the skip card, shows each goal's progress today
  (e.g. "Hamstring stretch 2/3" with a progress bar) and links to the goals screen. With no
  goals it invites the user to set one.
- **HOME-007** A "Stretch now" button on the goals card opens the stretch session screen
  (SESS-001) without waiting for a reminder.
- **HOME-008** The "Skip today" card has a close (X) button in its top-right corner. Closing it
  hides the card for the rest of today only; it shows again the next day. While it is hidden,
  "Skip today" stays one tap away in the top bar's overflow menu (and future days remain on the
  skipped-days screen).

  **Invariant — A skipped day is never hidden.** When today is skipped the card always shows,
  in its "Resume today" state and without the close button, whether or not it was closed
  earlier that day.

## Build checklist

- [x] Home state is derived from Room flows plus a 30-second clock tick (HOME-002).
- [x] Banners re-check permissions on resume (HOME-005).
- [x] The closed date is stored per device (SharedPreferences, as an epoch day) and compared
      with today, so it expires on its own (HOME-008).
- [x] Goal progress re-queries when the date changes (the clock tick drives the day bounds)
      (HOME-006).
