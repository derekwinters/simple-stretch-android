# Store listing text

The text of the Google Play listing: its title, short description and full description. This
page is the only place it is written; `.github/scripts/store_listing.py` compiles it into
`fastlane/metadata/android/en-US/` (`title.txt`, `short_description.txt` and
`full_description.txt`), and CI fails when those files, this page and the
[specification](../spec/README.md) are out of step
([store listing specification](../spec/store-listing.md#listing-text), STORE-LIST-001 to
STORE-LIST-007).

How to write it:

- Keep exactly the three sections below, in this order. Everything above `## Title` (this
  explanation) is not part of the listing.
- The title (at most 30 characters) and the short description (at most 80) are one line each.
- The title is also the app's name on the phone: the `app_name` string in
  `app/src/main/res/values/strings.xml` must say exactly the same, and a test fails when they
  differ ([STORE-LIST-008](../spec/store-listing.md#listing-text)). Change both together.
- Every line of the full description (at most 4,000 characters) is one claim, written as plain
  text and ending with the requirement IDs it rests on as an HTML comment, e.g.
  `<!-- GOAL-001, HOME-006 -->`. The comment does not show on this page or in the listing; view
  the page's source to see the IDs. Only claim what the cited requirements say.
- Every spec page marked `<!-- store: headline -->` needs at least one claim citing one of its
  IDs.
- After editing, run `python3 .github/scripts/store_listing.py` and commit the three files it
  writes with this page.

The category, tags, website and contact email are not part of this text; they are entered in
Play Console and recorded in [Play Console listing details](play-console.md).

## Title

Simple Stretch

## Short description

Stretch reminders on your own schedule, with daily goals and trends. <!-- SCHED-001, GOAL-001, TREND-004 -->

## Full description

Simple Stretch reminds you to stretch during the day, on a schedule you choose. <!-- SCHED-001 -->

- Make your own reminder schedules: pick the days, then set times or a repeat, like every hour from 9 to 5. <!-- SCHED-001, SCHED-011, SCHED-012 -->
- Start with a set of common stretches, and add or edit your own. <!-- LIB-001, LIB-002 -->
- Tap a reminder to open your stretches, check off what you did and save it. <!-- SESS-001, SESS-002, SESS-003, NOTIF-002 -->
- Not a good moment? Snooze a reminder for 5 minutes. <!-- NOTIF-008 -->
- Set a daily goal for each stretch and see today's progress on the home screen. <!-- GOAL-001, GOAL-003, HOME-006 -->
- Reminders go quiet while all of today's goals are met. <!-- GOAL-005, NOTIF-010 -->
- Skip today, or any day ahead, without changing your schedules. <!-- SKIP-001, SKIP-002, SKIP-006 -->
- See how well you kept your goals by week or month, with your current streak. <!-- TREND-004, TREND-005, TREND-006 -->
- Add a home screen widget with a ring that fills as you meet today's goals. <!-- WIDGET-001, WIDGET-002, WIDGET-003 -->
- No account, no ads and no internet access: the app never goes online, so it never sends what you record anywhere itself. <!-- PRIV-001, PRIV-002 -->
