# Product vision

## The problem

A student's day at a public school is spread across systems that do not talk
to each other: a timetable, attendance tooling, notices, an LMS and other
institutional sources. Answering "what do I have now?" should not require
opening several products or asking another person.

## The vision

One app that answers the question a student actually has — **"what does my day
look like right now?"** — using whatever the school already runs, without
forcing the school to replace its systems.

## Principles

**Today first.** The app prioritizes the current class, next class and immediate
changes before exposing lower-frequency account or administrative surfaces.

**Works without a network.** School networks drop. A student still needs their
own saved timetable, with an honest indication that it may be stale.

**Integrates, does not replace.** Institutional systems remain systems of
record. Compañero normalizes and presents their information.

**Built for real conditions.** Low-end hardware, small screens, bright
hallways, tired users and intermittent connectivity are design inputs.

**Honest when it does not know.** Unknown, stale and unavailable information
are visibly different from current information.

**Account data is isolated.** Cached academic data is scoped to the active
platform identity. A second student on the same device must never inherit the
first student's academic snapshot.

## Who it is for

| Audience | What they need |
|---|---|
| Student (primary) | Today, agenda, due work, changes and attendance actions |
| Teacher (secondary) | Class roster and attendance session operations |
| Parent (later) | An authorized view of the student's day |

The current product work remains student-first.

## What success looks like

- A student opens the app and understands the current/next class in seconds.
- Agenda works directly and remains useful offline.
- Session expiry returns the app to authentication instead of trapping the user
  in repeated 401 errors.
- Attendance, when added, takes under ten seconds in the normal flow.
- No school is required to replace its identity, SIS or LMS.

## Non-goals

- Not a social network.
- Not a replacement SIS.
- Not a gradebook authoring system.
- Not an LMS replacement.
- Not a continuous location tracker.

## Current status

The technical foundation is merged to `main`.

The active product-foundation candidate adds identity/session handling, secure
token storage, student-scoped academic cache, a canonical academic repository,
Hoy, Agenda, profile/logout and the first reusable student UX components.

Real pilot-school identity and academic providers remain external integration
work. See [Master Plan](MASTER_PLAN.md) and [Roadmap](ROADMAP.md).
