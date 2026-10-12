# V14 — Visible labels and six-role navigation regression checks

The existing `CompaneroBottomBar` now displays labels as well as icons. TalkBack roles and labels, attendance QR emphasis and reduced-motion behavior remain intact. The bar grows vertically at system font scale 1.5+.

The new Android instrumented suite verifies real NavHost navigation stacks for STUDENT, TEACHER, TUTOR, COORDINATOR, ADMIN and SUPER_ADMIN with inert content routes. It checks a secondary screen, back button and return to the role-specific root. Real identity, school Wi-Fi, TalkBack, 360/390/430dp screenshots and large-text usability still require separate end-to-end acceptance.

Base: `03d2069c4ac1aeaf7daf6227a4ff8b814264ac50`.
