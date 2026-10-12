# V14 — UX integrity and truthful information (2026-10-12)

Base SHA: `2f8e3ebdfbdaf821e156ea1b997481321fb5c861`. Branch: `fix/v14-ux-truth-navigation-20261012`.

## Confirmed problems in source

- **P0 Coordination:** the home page contained fixed values for group count, teacher attendance and incidents. They were removed; an honest pending-integration message remains until there is an authorized, institution-scoped metrics API. **The live metrics integration is NOT implemented.**
- **P1 Channel:** the student interface mislabeled the response controls as a free-form Chat tab. It now offers Publications and Resources. The latter filters existing material posts and announcements with attachments, and never invents conversations. Student quick responses remain available on eligible posts in both views.
- **P2 Schedule:** the selected day previously used `remember(state.entries)`, resetting it on refresh and returning incorrectly to another weekday. The selected preference is saveable, with a display-only fallback for temporarily unavailable days. The selected subject filter is preserved while absent topics safely fall back to all.

## Tests added

- `ChannelFeedFilterTest`: stable ordering, attachments and material posts, empty resource state.
- `ScheduleDaySelectionTest`: Saturday persistence and temporary data gaps.

## Deliberately not represented as finished

- Real coordinator statistics require an explicitly authorized API and verified aggregation, not made-up figures.
- The V8 light/dark/contrast resolver, real device TalkBack, full six-role instrumentation, institutional grade sync, and full V14 release remain open.
- A green source-level CI result is not a substitute for device acceptance.

## Validation

Run existing Android compile/test/lint/instrumentation workflows on the PR HEAD. Do not merge on failure. Report actual GitHub Actions conclusions, without asserting local execution.
