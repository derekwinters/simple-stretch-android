# Store screenshot captions

The caption on each Google Play phone screenshot, one entry per shot ID of the
[store listing specification](../spec/store-listing.md). This file is the only place the text
lives: the screenshot test reads it, and a caption that does not fit (at most 3 headline lines
and 2 supporting lines, as rendered) fails that test (STORE-SHOT-012, STORE-SHOT-014).

Each entry is a `##` heading with the shot ID, then a `Headline:` line and a `Supporting:` line.
Changing a caption changes its image, which is then re-recorded in the same pull request (the
`record-store-screenshots` label, or `./gradlew recordRoborazziDebug` locally; STORE-SHOT-009).

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
