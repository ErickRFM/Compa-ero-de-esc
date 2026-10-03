# Visual Baseline V4

This file defines the screenshot and device matrix that every major UX/UI V4
change must preserve.

## Phone sizes

- 360 x 800
- 390 x 844
- 430 x 932

## Adaptive widths

- compact: < 600dp
- medium: 600-839dp
- expanded: >= 840dp

## Themes

- light
- dark

## Font scales

- 1.0
- 1.3
- 2.0

## Motion modes

- normal
- reduced motion

## Connectivity/data states

- online/fresh
- slow refresh
- offline with cache
- offline without cache
- sync pending
- session expired
- server error

## Canonical screenshots

1. Login idle
2. Login loading
3. Login error
4. Hoy current-class hero
5. Hoy between classes
6. Hoy no classes
7. Hoy offline/stale
8. Agenda day
9. Agenda week
10. Agenda empty
11. Attendance open
12. Attendance pending sync
13. QR scanning
14. QR verifying
15. Attendance verified
16. Attendance review required
17. Attendance rejected
18. Profile/logout
19. Notification center (when implemented)

## Review rule

A screenshot change must be explainable as one of:
- intended visual-system change;
- state-correctness fix;
- adaptive/accessibility correction.

Unexpected layout drift blocks merge.
