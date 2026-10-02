# Master Plan — Compañero de Escuela

Status: active execution plan
Baseline: `feat/product-foundation-v2`
Date: 2026-10-02

## Product goal

Compañero de Escuela is an academic companion, not a collection of school
modules. The primary student experience must answer, in seconds:

- what class is happening now;
- where it is;
- what comes next;
- whether something changed;
- what needs action;
- whether the information is current or locally cached.

The interaction model is **context -> action -> detail**.

## Product principles

1. **Today first.** The default surface is the student's day, not a module grid.
2. **Offline is normal.** Cached academic data remains useful when the network
   or institutional system is unavailable.
3. **One account, one data scope.** Cached data is always keyed and read by the
   authenticated platform subject.
4. **Human states.** UI never exposes HTTP, database, provider or sync jargon.
5. **Contextual actions.** Attendance appears on the relevant class instead of
   permanently occupying primary navigation.
6. **No speculative modules.** A module is added only with real code, tests and
   a stable responsibility.

## Execution order

### Block A — product foundation

- [x] foundation merged to main;
- [x] secure token storage;
- [x] local token identity/expiry inspection;
- [x] session becomes observable and expires locally;
- [x] 401 invalidates the local platform session;
- [x] academic cache scoped by student id;
- [x] one `core:academic` repository for API + Room fallback;
- [x] Home and Agenda consume the same academic source;
- [x] Git history reconciled with main;
- [ ] real pilot-school identity provider;
- [ ] real pilot-school academic provider;
- [ ] Actions execution restored externally.

### Block B — UX system

- [x] primary navigation reduced to Hoy + Agenda;
- [x] Profile moved to contextual app-bar navigation;
- [x] visible logout;
- [x] reusable academic class card;
- [x] reusable compact timeline;
- [x] reusable human-readable status notices;
- [x] password visibility and IME submit in login;
- [x] Hoy contextual hierarchy;
- [x] Agenda day/week modes;
- [ ] string/resource localization pass;
- [ ] adaptive tablet navigation;
- [ ] Compose UI and accessibility test suite.

### Block C — academic model v2

Next after Block A verification:

- `AcademicTerm`;
- `Course`;
- `Group`;
- `SchedulePattern`;
- `ClassOccurrence`;
- `ScheduleOverride`;
- class states such as scheduled, cancelled, rescheduled and online;
- explicit change metadata for room, teacher and time.

This block is intentionally not mixed into the current identity/offline
consolidation PR.

### Block D — pending work

After the academic model is stable:

- tasks / due work;
- overdue/completed states;
- institutional announcements;
- calendar/events;
- LMS summaries/links.

### Block E — attendance

Domain order:

`Course -> Group -> ClassOccurrence -> AttendanceSession -> AttendanceAttempt -> AttendanceRecord`

Required properties:

- idempotent attempts;
- offline outbox;
- server reconciliation;
- VERIFIED / REVIEW_REQUIRED / REJECTED states;
- dynamic signed QR as evidence, not as the attendance verdict;
- teacher roster and review flow.

### Block F — notifications and pilot

- internal notification center;
- local reminders;
- optional push after product need is proven;
- real-device pilot;
- UX timing and offline measurements;
- release gate.

## Git policy

The current consolidation line is:

`main -> feat/product-foundation-v2`

New work after this PR merges must branch from updated `main`. Avoid parallel
branches that independently implement identity, cache, Today and Schedule.

Every PR must provide:

- one primary responsibility;
- tests for new domain rules;
- build/lint/test evidence when runners are available;
- explicit external blockers when they are not;
- no secrets;
- updated architecture/product docs;
- loading, empty, offline and error behavior for user-facing work.

## Release gates

A feature is not done until:

- merged to main;
- tests actually execute;
- Android and API build reproducibly;
- lint has no errors;
- data ownership is enforced;
- offline/error behavior is defined;
- accessibility has been reviewed;
- docs match runtime behavior.

## Pilot definition

The first pilot should let a student:

1. authenticate with a real institutional identity;
2. open Hoy and understand the current/next class;
3. open Agenda directly without first visiting Hoy;
4. restart offline and still see their own saved schedule;
5. never see another account's cached data;
6. be returned to login when the platform session expires;
7. see honest stale/offline messaging.

Attendance is added only after those seven behaviors are demonstrated.
