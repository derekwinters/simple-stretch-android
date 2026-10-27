# Store screenshot captions

The caption on each Google Play phone screenshot, one entry per shot ID of the
[store listing specification](../spec/store-listing.md), and last the text of the feature
graphic. This file is the only place the text lives: the screenshot and feature graphic tests
read it, and a caption that does not fit (at most 3 headline lines and 2 supporting lines, as
rendered) fails that test (STORE-SHOT-012, STORE-SHOT-014, STORE-GRAPHIC-004).

Each entry is a `##` heading with the shot ID, then a `Headline:` line and a `Supporting:` line.
Changing a caption changes its image, which is then re-recorded in the same pull request (the
`record-store-screenshots` label, or `./gradlew recordRoborazziDebug` locally; STORE-SHOT-009).

The feature graphic's entry, `STORE-GRAPHIC-003`, is a pair of claims: each of its two lines
ends with the requirement IDs it rests on as an HTML comment, e.g. `<!-- SCHED-001 -->`, as in
the [listing text](listing.md). The comment is not drawn; the test fails if a line has none or
cites an ID the specification does not define or has retired (STORE-GRAPHIC-003). Only claim
what the cited requirements say.

## STORE-SHOT-001

Headline: Stretch reminders that fit your day

Supporting: Today's reminders and goal progress at a glance.

## STORE-SHOT-002

Headline: Log a stretch in two taps

Supporting: The reminder's stretches come first.

## STORE-SHOT-003

Headline: Set a daily goal for each stretch

Supporting: Tap + or − and it saves.

## STORE-SHOT-004

Headline: Repeat every hour through your workday

Supporting: Or set exact times, any days you like.

## STORE-SHOT-005

Headline: See your week and keep the streak

Supporting: Daily scores, weekly and monthly.

## STORE-GRAPHIC-003

Headline: Your stretch routine, on your schedule <!-- SCHED-001, SCHED-002 -->

Supporting: Reminders, daily goals and weekly trends. <!-- NOTIF-001, GOAL-001, TREND-004 -->
