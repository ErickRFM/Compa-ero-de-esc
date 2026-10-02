# Product vision

## The problem

A student's day at a public school is spread across systems that do not talk
to each other: a timetable posted on a wall, an attendance sheet passed down a
row, a notice board, a library app nobody has installed. Answering "what do I
have now" and "am I in trouble for absences" means asking a person.

The institutional systems that hold this data are typically old, partial, and
inconsistent between schools. Some schools have an API. Most do not.

## The vision

One app that answers the question a student actually has — **"what is my day
looking like?"** — using whatever the school already runs, without asking the
school to change anything.

## Principles

**Works without a network.** School networks drop. A student in a corridor
with no signal still needs today's timetable. The app is designed offline-first
and syncs when it can, not the other way round.

**Integrates, does not replace.** We read the school's systems. We never ask
the school to adopt ours.

**Built for the worst conditions, not the demo.** Low-end hardware, small
screens, bright sunlight, a tired student, a projector. Design decisions are
made against those conditions because that is the real usage.

**Honest when it does not know.** If the timetable cannot be resolved, it says
so. A confident wrong answer is worse than a visible gap.

**Institutional data stays put.** Credentials, location history and attendance
records are handled under a model a parent could read and agree with. See
[Location privacy](../privacy/LOCATION_PRIVACY.md).

## Who it is for

| Audience | What they need |
|---|---|
| **Student** (primary) | Today at a glance, absences, due work, notices |
| **Teacher** (secondary) | Class roster, mark attendance quickly |
| **Parent** (tertiary) | An accurate view of their child's day |

The student is the only audience designed for at this stage.

## What success looks like

- A student opens the app once in the morning and knows their day.
- Attendance takes under ten seconds, including offline.
- No school is asked to install or configure anything.
- A school whose systems are a PDF and a spreadsheet is still supported.

## Non-goals

- Not a learning management system. It links to and summarises the school's
  LMS; it does not compete with it.
- Not a social network. No messaging between students.
- Not a school information system. It does not replace the school's records,
  and it is not the system of record for anything.
- Not a gradebook. Grades are read and shown, never authored here.

## Current status

Foundation only. The build, the API, the Android shell and the integration
boundary are real and tested. No product feature is implemented. See the
[Roadmap](ROADMAP.md).
