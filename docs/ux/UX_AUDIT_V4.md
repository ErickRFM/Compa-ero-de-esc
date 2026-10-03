# UX/UI V4 audit

Status: baseline audit for the Expressive/Motion product line.

## Current product baseline

The current Android product is functional and intentionally student-first:

- Login uses Material 3 text fields, password visibility and IME submit.
- Hoy shows greeting/date, current class, next class and a compact timeline.
- Agenda supports day/week views and offline fallback.
- Navigation uses a conventional Material 3 bottom bar with Hoy and Agenda.
- Shared UI includes class cards, timeline items, status notices and generic content states.
- Theme uses indigo/teal/amber/red, enlarged typography and rounded shapes.

This is a sound information architecture, but the presentation is still conservative.
The next line must improve expression and continuity without weakening offline,
accessibility, low-end-device performance or the existing feature boundaries.

## Main UX gaps

### Visual identity
- Most surfaces are standard Material containers.
- The brand does not yet have a recognizable hero surface or motion language.
- Current navigation looks framework-default rather than product-specific.
- Login is usable but visually generic.
- Dark mode is technically present but not art-directed as a premium surface.

### Motion
- No central motion token system.
- No shared element navigation.
- No animated day transition in Agenda.
- Current class changes are not expressed as state transitions.
- Offline/sync/attendance states do not morph between states.
- No reduced-motion policy beyond platform defaults.

### Feedback
- Important actions rely mostly on text/state replacement.
- Attendance/QR needs visual confirmation and haptics.
- Sync pending -> synced should be visible without forcing user refresh.

### Adaptive UX
- Phone layout is the primary implementation.
- No navigation rail/two-pane tablet composition yet.
- Large-font and compact-width behavior need explicit gates.

## Keep

Do not redesign these concepts away:

1. Hoy remains the primary destination.
2. Agenda remains a separate high-frequency destination.
3. Profile remains secondary.
4. Offline data remains explicit and honest.
5. Attendance remains a short task, not an administrative workflow.
6. Feature modules do not depend on each other.
7. Material 3 remains infrastructure, not the visual identity itself.

## V4 target

Compañero should feel like a modern Android companion rather than a school portal:

- contextual hero for the current class;
- expressive floating navigation;
- continuous shared-element transitions;
- animated agenda/day switching;
- stateful attendance and QR experience;
- selective Rive/Lottie use;
- haptics for meaningful confirmations;
- premium light/dark surfaces;
- adaptive phone/tablet shell;
- performance and accessibility measured as release gates.

## Technology policy

Primary:
- Jetpack Compose animation APIs;
- SharedTransitionLayout/sharedBounds/sharedElement;
- AnimatedContent / AnimatedVisibility;
- Animatable, updateTransition and rememberInfiniteTransition;
- Canvas/graphicsLayer/Brush for lightweight custom effects;
- Material 3 as stable foundation.

Selective:
- Rive only for brand / QR / attendance state-machine experiences.
- Lottie only for short-lived empty/success/offline illustrations.

Reference projects:
- android/compose-samples for official Compose patterns;
- android/nowinandroid for adaptive/performance/testing patterns;
- skydoves/compose-animations as animation laboratory;
- rive-app/rive-android for state-machine animation;
- airbnb/lottie-android for state illustrations.

Rule: do not add a runtime dependency when a maintainable Compose implementation
can solve the same interaction with a small amount of code.

## Screen priorities

P0:
1. shell/navigation;
2. Login;
3. Hoy;
4. Agenda;
5. Attendance;
6. QR.

P1:
7. notifications;
8. class detail;
9. offline/sync states;
10. dark mode.

P2:
11. tablet/two-pane;
12. extended decorative animation.

## Acceptance

No V4 visual PR is complete without:
- light/dark screenshots;
- compact phone verification;
- 48dp target check;
- semantics where interaction changed;
- reduced-motion behavior;
- no new lint errors;
- real Android unit/UI tests where applicable;
- no measurable regression in the key interaction budget.
