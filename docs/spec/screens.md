<!-- store: omit -->

# Screens

The app has five top-level screens, reached from a bottom navigation bar (HOME-004): Home,
Schedules, Goals, Trends and Settings. The approved
[mockups](../design/bottom-navigation/bottom-navigation.png) fix which tabs exist, their order
and labels, what each screen holds and where, and the no-goals and skipped-day states; they do
not fix icons, colours, type, spacing or the figure drawing, which follow the app's theme
(`StretchTheme`, including dynamic colour).

## Home

- **HOME-001** The home screen shows, from the top: the banners (HOME-005), today's date, the
  goal ring (HOME-006) with "Stretch now" under it (HOME-007), and the Today list (HOME-002).
  There is no schedule list (HOME-003 is the Schedules tab) and no skip card. See the
  [mockups](../design/bottom-navigation/bottom-navigation.png) ("Home", "Home, no goals",
  "Home, today skipped").
- **HOME-002** The Today list: today's reminders from all enabled schedules, each with the
  time, stretches and schedule name. **Upcoming reminders come first**, in time order; below
  them, after a divider, **today's past reminders**, in time order and greyed out. A reminder
  at or before the current time is past. A repeating schedule shows as one row at its next slot
  today, with its repeat summary and how many slots are left today, among the upcoming rows;
  once its last slot today has passed it moves to the greyed past rows, at that last slot. When
  today is skipped, every row is greyed, upcoming and past alike (the two groups keep their
  order). With no reminders today the list says so. The "Today" heading row carries a text
  button at its right end (SKIP-001): "Skip today" while today is not skipped (enabled when at
  least one reminder is left today, shown disabled when none are), and "Resume today" once
  today is skipped. This is the only place on Home to skip or resume today. Every row opens the
  stretch session when tapped (HOME-009).

  **Invariant — When today is skipped, the Today heading always shows "Resume today".** No
  state hides it: not an empty Today list, not the time of day, and nothing stored per device.

  **Invariant — Which rows are upcoming and which are past is a pure function of the rows and
  the time read from the app clock (SCHED-016)**, never of when the screen was opened, so a
  reminder moves below the divider at its time while Home is shown.
- **HOME-003** _Retired._ It described the schedule list on the home screen, which moved, with
  its switches and its invariant, to the Schedules tab (HOME-010).
- **HOME-004** A bottom navigation bar (Material 3 `NavigationBar`) is shown on each of the
  five top-level screens, with five items in this order: **Home**, **Schedules**, **Goals**,
  **Trends**, **Settings**. Every item has an icon and its text label, always shown; the
  selected item is highlighted. Each top-level screen is one tap from every other. Screens
  opened from a top-level screen (the schedule editor, skipped days, my stretches, a trends day,
  the stretch session) cover the whole window and have no bar; Back returns from them.

  Back on any top-level screen other than Home returns to Home; Back on Home leaves the app.
  Switching tabs keeps each tab's own state (its scroll position and any screen opened above
  it) and never stacks one tab on another.

  The top bar has no icon actions and no overflow menu. Home's top bar title is the app's
  name, "Simple Stretch", read from `app_name` (STORE-LIST-008); each other top-level screen's
  title is its own name ("Schedules", "Daily goals", "Trends", "Settings"), with no back arrow.

  **Invariant — Every bar item is labelled.** No tab is an unlabelled icon, and the labels show
  whether or not the item is selected.

  **Invariant — The stretch library is reached by a labelled item** (Settings, HOME-011), never
  only by an unlabelled icon.
- **HOME-005** Banners appear at the top of Home, and only on Home, when notifications are
  blocked (NOTIF-005) or exact alarms are not allowed (SCHED-007).
- **HOME-006** Below the date, the **goal ring**: the home screen widget's segmented ring
  (WIDGET-002 to WIDGET-006), with one arc per current goal (GOAL-008), each filling with that
  goal's progress today, and in its centre the launcher icon's side-bend figure and today's
  count "*done*/*total*" (or a check mark once every goal is met), exactly as the widget draws
  them. With no goals the ring is the widget's dotted empty track with the dimmed figure, and a
  **"Set a goal"** button beside "Stretch now" opens the Goals tab. The ring is drawn in the
  app's theme colours (primary for arcs and figure, onSurface for text, outline for the mat),
  and its content description says the count (e.g. "Today's goals: 4 of 8 done").

  **Invariant — Home's ring and the widget never disagree.** Both are built by the same
  `GoalRing.fromLog` from `GoalMath.goalRows` (WIDGET-009) and drawn by the same renderer; Home
  has no ring maths of its own.
- **HOME-007** A "Stretch now" button under the ring opens the stretch session screen
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

## Schedules

- **HOME-010** The Schedules tab, titled "Schedules", lists every schedule with its name, its
  days and its times (or repeat summary, e.g. "Every 60 min at :50, 8:00 AM - 5:00 PM") and an
  enabled switch. Tapping a schedule opens its editor; a "New schedule" button creates one. At
  the top, a labelled **"Skip other days"** button opens the skipped-days screen (SKIP-002).
  With no schedules it invites the user to create one. (It replaces HOME-003.)

  **Invariant — Toggling a schedule's switch reschedules immediately**; there is no separate
  save step for it.

## Settings

- **HOME-011** The Settings tab, titled "Settings", holds a list of labelled items. For now it
  has one: **"My stretches"**, with the subtitle "Add, rename or remove stretches", which opens
  the stretch library (LIB-006).

## Build checklist

- [x] Home state is derived from Room flows plus a 30-second tick that reads the app clock
      (HOME-002, SCHED-016).
- [x] Today rows' past state is unit tested with a fixed clock (HOME-002).
- [x] The Today list's split into upcoming and greyed past rows, and its greying when today is
      skipped, is a pure, unit-tested function, including a repeating schedule moving to the
      past rows after its last slot (HOME-002).
- [x] Banners re-check permissions on resume (HOME-005).
- [x] The exact-alarm banner is the only place the user is asked for exact alarms: no prompt at
      first launch or on first schedule save (HOME-005, SCHED-007).
- [x] Banners are drawn by Home's content only; a Robolectric test finds them on Home and not
      on the other tabs (HOME-005).
- [x] Today heading: a "Skip today" / "Resume today" text button at its right, disabled when
      nothing is left today; its state is a pure, unit-tested function, and a skipped day gives
      "Resume today" even with an empty list (HOME-002).
- [x] No skip-card closed date is written or read (HOME-008 retired).
- [x] Bottom bar: five labelled items in the order Home, Schedules, Goals, Trends, Settings;
      tab navigation pops up to Home with saved state, restores state and is single-top; a
      Robolectric test taps every tab from every other and checks the labels (HOME-004).
- [x] Back from every other tab returns to Home; on Home the navigation host holds no Back
      callback, so Back leaves the app (Robolectric test, HOME-004).
- [x] No top-bar icon actions and no overflow menu on any top-level screen (HOME-004).
- [x] The Home top bar's title is `stringResource(R.string.app_name)`, never a literal; a
      Robolectric test renders Home and finds the `app_name` text (HOME-004, STORE-LIST-008
      invariant).
- [x] The ring is `GoalRing.fromLog` drawn by `GoalRingRenderer` into the Compose canvas with
      the theme's colours; its done/total and empty state are unit tested (HOME-006 invariant).
- [x] Goal progress re-queries when the date changes (the clock tick drives the day bounds)
      (HOME-006).
- [x] "Stretch now" opens the session and "Set a goal" opens the Goals tab (Robolectric test,
      HOME-006, HOME-007).
- [x] Today rows build their session route with the same pure, unit-tested function as the
      notification intent, from the reminder's stretch ids, schedule name and today's time
      (HOME-009 invariant).
- [x] "Skip other days" on Schedules and "My stretches" on Settings open their screens
      (Robolectric test, HOME-010, HOME-011).
- [x] Store listing reviewed: this page is `store: omit`, and any claim in
      `docs/store/listing.md` that cites it is still true (STORE-LIST-002, STORE-LIST-003).
