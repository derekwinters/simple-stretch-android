# Widgets

A home screen widget shows today's goals at a glance. The segmented ring is the first widget;
the mat-bar and goal-list widgets are later alternatives.

### Segmented ring

- **WIDGET-001** The widget is 2×2 cells by default and resizable in both directions. It is
  drawn square, as large as the smaller side allows, centred in its cell area. It has no widget
  background: only the disc (WIDGET-005), the ring and the centre are drawn.
- **WIDGET-002** The ring has one arc per **current goal** (GOAL-008), in goals-screen order
  (GOAL-002: by stretch name, ignoring case, ties by id), clockwise from 12 o'clock. The arcs
  share the ring equally with a gap of 8° between neighbours; one goal is a full ring with no
  gap. Arc ends are round, and the 8° is the visible gap between the round ends. Each arc is a
  track in the track colour with the filled part drawn over it.
- **WIDGET-003** Each arc fills clockwise from its start by that goal's progress / count
  (GOAL-003), capped at full: 4 of 3 is a full arc. The fill is continuous (2 of 3 is two
  thirds of the arc), and in-progress and met arcs use the same colour.
- **WIDGET-004** The centre shows the launcher icon's side-bend figure on its mat and, under
  it, today's count as "*done*/*total*": *total* is the sum of the current goals' counts and
  *done* the sum of each goal's progress capped at its count (so 4 of 3 adds 3). Once every
  goal is met, the count is replaced by a plain check mark in the text colour, with no circle
  or badge.
- **WIDGET-005** Behind the ring is a disc in the disc colour at 55% opacity, reaching the
  ring's inner edge, so the widget reads on black and on light wallpapers.
- **WIDGET-006** States:
  - **Nothing done yet** (at least one goal, *done* = 0): the figure is dimmed and the arcs
    show only their track; the count reads "0/*total*".
  - **No goals set**: the ring is a dotted track (round dots in the track colour, no arcs),
    the figure is dimmed and centred, and there is no count and no check mark.
  - Otherwise the figure is drawn in the arc colour.

  "Dimmed" means the figure (body and head) is drawn in the text colour at 40% opacity; the mat
  keeps its colour.
- **WIDGET-007** Tapping the widget opens the app on its home screen (HOME-001): any screen
  open above home is closed.

### Colour

- **WIDGET-008** The widget follows the system light/dark mode. Where the phone supports
  dynamic colour (Android 12 and later, the same test as the app's theme) it uses the
  wallpaper colours, with the roles Material 3's dynamic schemes use:

  | Part | Role | Light | Dark |
  | --- | --- | --- | --- |
  | Arcs and figure | primary | `system_accent1_600` | `system_accent1_200` |
  | Disc (at 55%) | surface | `system_neutral1_10` | `system_neutral1_900` |
  | Text and check mark | onSurface | `system_neutral1_900` | `system_neutral1_100` |
  | Empty track | onSurface at 14% (light) / 18% (dark) | | |
  | Mat | outline | `system_neutral2_500` | `system_neutral2_400` |

  Without dynamic colour it uses the mockup's fixed values:

  | Part | Light | Dark |
  | --- | --- | --- |
  | Arcs and figure | `#1361A7` | `#3D86D1` |
  | Disc (at 55%) | white | `#121212` |
  | Text and check mark | `#1C1B1F` | `#F5F5F5` |
  | Empty track | black at 14% | white at 18% |
  | Mat | `#818589` | `#818589` |

  **Invariant — The fixed mockup colours are used only when dynamic colour is unavailable.**
  The choice between dynamic and fixed is made in one place, by the same Android-version test
  as the app's theme; no part of the widget mixes the two.

  Both the light and the dark drawing are produced at every redraw and the launcher shows the
  one for the current mode, so switching between light and dark needs no redraw. A change of
  wallpaper colours shows at the next redraw (WIDGET-010).

### Data and refresh

- **WIDGET-009** The widget's goals and progress come from the same calculation as the goals
  screen (`GoalMath.goalRows`, GOAL-002/003): current goals only (GOAL-008), with progress
  counted from the completion log over today's local calendar day.

  **Invariant — Progress is derived from the completion log, never stored** (GOAL-004). The
  widget keeps no counter and no copy of progress between redraws; each redraw reads the log.

  **Invariant — Only today's completions count.** Each redraw counts completions from local
  midnight today to the next local midnight (`GoalMath.dayBounds`), never a rolling 24 hours or
  a day fixed when the widget was added.
- **WIDGET-010** The widget redraws:
  - after every logged completion (SESS-003);
  - after every goal add, change or removal (GOAL-009), and after a stretch is renamed or
    deleted (which changes the arcs' order or removes a goal, LIB-005);
  - at local midnight, through an alarm set for the start of the next local day at each
    redraw;
  - when it is added or resized, when the app starts, after a reboot, a time or time-zone
    change, or an app update.

  **Invariant — The widget never shows a number the app would not.** Every write that can
  change today's goals or progress (completions, goals, stretches) is followed by a redraw,
  made from the repository, not from a screen, so no screen can forget it; and the midnight
  alarm is re-armed at every redraw, so a new day always starts at 0.

## Build checklist

- [x] `GoalRing.model` is pure and unit tested for 0, 1, 3 and 8 goals; none, partly and all
      met; progress above the count; and no goals (WIDGET-002 … WIDGET-006).
- [x] `GoalRing.fromLog` builds the ring from `GoalMath.goalRows`, the goals screen's
      calculation, and drops stretches without a goal (WIDGET-009).
- [x] `GoalRing.nextRedrawAt` is the next local midnight, including across a daylight-saving
      change (WIDGET-010).
- [x] `WidgetPalette.select` returns the fixed mockup values only when dynamic colour is
      unavailable, and its fixed values match WIDGET-008 (unit tested).
- [x] The renderer draws round arc ends inside each arc's share, so the visible gap is 8°
      (WIDGET-002).
- [x] `StretchRepository` requests a redraw after `logCompletions`, `setGoal`, `removeGoal`,
      `saveStretch` and `deleteStretch` (WIDGET-010 invariant).
- [x] The midnight alarm is re-armed at every redraw and cancelled when the last widget is
      removed (WIDGET-010).
- [x] Tapping opens home with any screen above it closed (WIDGET-007).
- [ ] On a device: light and dark, dynamic colour on and off, resize, tap, and the midnight
      reset (human-only issue).
