# Trends

How well the daily goals (goals.md) were kept, by week or month, with a drill-down into one day.
Everything here is derived from the goal history (GOAL-008), the completion log (SESS-004) and
the skipped dates (SKIP-007); nothing is stored.

- **TREND-001** A day is judged by the goals **in force that day** (GOAL-008), not by today's
  goals. A goal changed on the 5th from 3 to 1 a day asks for 3 on the 4th and 1 on the 5th; a
  goal removed on the 7th no longer counts from the 7th but still counts before it.

  **Invariant — Changing or removing a goal never changes a past day's score.** Past days are
  scored from the goal rows whose range covers them, which an edit closes rather than
  overwrites (GOAL-009).
- **TREND-002** A day's **score** is the sum, over the goals in force that day, of
  min(completions of that goal's stretch that day, its target), divided by the sum of the
  targets. Completions count by local calendar day as in GOAL-003. Example: hamstring 3 a day and
  calf 2 a day; 4 hamstring and 1 calf gives (3 + 1) / 5 = 80%. A day is **all goals met** when
  every goal in force reached its target (score 100%).

  **Invariant — Extra completions never lift a score.** Completions beyond a goal's target, and
  completions of stretches without a goal that day, add nothing: a day never exceeds 100%, and
  over-doing one goal never makes up for another.
- **TREND-003** A day with no goals in force, a skipped day (SKIP-001, SKIP-002) and a day after
  today have **no score**. They are shown as gaps and left out of every average, count and
  streak.

  **Invariant — A gap is never 0%.** A day without a score is never drawn as a 0% bar or
  averaged in as 0; a scored day with nothing done is 0% and is drawn differently from a gap.
- **TREND-004** The **Trends** screen has a Week / Month toggle (Week first) and previous / next
  buttons that move one period at a time. A week runs **Monday to Sunday**; a month runs from
  the 1st to its last day. The period's title reads like "Oct 5 – 11, 2026" or "October 2026".
  Next is disabled once the period contains today (no browsing into the future); previous is
  disabled once the period starts on or before the first day any goal was in force. Switching
  between week and month shows the period containing today when today is in view, otherwise the
  period containing the first day shown. A bar chart shows one bar per day of the period, its
  height the day's score on a 0–100% axis; all-met days are drawn in the full accent colour,
  partial days lighter, gaps as a dashed outline at the baseline, and today is underlined.
- **TREND-005** Above the chart, a summary for the shown period: the **average score** over the
  scored days in it (and how many days that is; "–" when none), and the **number of days with
  all goals met**.
- **TREND-006** The summary also shows the **current streak**: the number of consecutive
  all-goals-met days ending today or yesterday, counted back through all history (not only the
  shown period). Today adds to the streak once it is met but never breaks it, because the day
  is not over. Gaps (TREND-003) are passed over: a skipped day or a day without goals neither
  adds to nor breaks a streak. The first earlier scored day that was not fully met ends it.
- **TREND-007** Tapping a day's bar (today or earlier) opens the **day detail** screen: the
  day's score (or why it has none), each goal in force that day with done / target and a
  progress bar, and every completion logged that day with its time, in time order (including
  stretches without a goal).
- **TREND-008** The day detail screen also shows the **score through the day** as a step chart:
  x is the time of day from 0:00 to 24:00, y is 0–100%. It starts at 0% at midnight and steps up
  at the time of each completion that counts towards a goal (TREND-002), staying level in
  between; completions saved together (one session save, SESS-003) make one step. The line
  runs to the current time for today and to midnight for past days. A day without goals in
  force has no chart. If the clocks go back during the night, time of day never runs
  backwards on the chart.
- **TREND-009** The Trends screen is reached from a labelled "Trends" item in the home screen's
  overflow menu (HOME-004) and from a "Trends" button on the home goals card while goals exist
  (HOME-006).
- **TREND-010** **Accessibility:** each day's bar is its own accessible element whose content
  description gives the date and the score, done / target and "all goals met" (or "skipped",
  "no goals", "not yet"), and announces tapping as "Show day". The step chart has one content
  description listing each step's time and score. Charts are drawn with Compose `Canvas`; no
  chart library is used.

## Build checklist

- [x] `TrendMath` (goals in force, daily score, per-day counts, week/month periods, summary,
      streak, intraday series) is pure and unit tested (TREND-001..006, TREND-008).
- [x] Scores are `null` for gaps, and the average uses only non-null scores (TREND-003
      invariant).
- [x] Credited completions are `min(done, target)` per goal (TREND-002 invariant).
- [x] Weeks start Monday via `TemporalAdjusters.previousOrSame(MONDAY)`, independent of locale
      (TREND-004).
- [x] The streak is computed over all days since the first goal, not the shown period
      (TREND-006).
- [x] Each bar is a separate composable with `clearAndSetSemantics { contentDescription }` and
      a labelled click (TREND-010).
- [x] Only completions since the first goal day are read for the trends screen (earlier ones
      can never score).
- [ ] Compose UI test of the chart semantics (needs an emulator in CI).
