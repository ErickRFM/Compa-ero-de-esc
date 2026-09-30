# Bug register

Defects found during the foundation phase. These are recorded as architecture
lessons, not as anecdotes: each one is now protected by a test that fails
without the fix.

The common thread is that **all five were invisible to a green build.**

---

## BUG-001 — `requireEnvironmentSatisfied` was never invoked

| | |
|---|---|
| **Severity** | Critical |
| **Status** | Fixed |
| **Found by** | Code review of the boot path, during the final audit |
| **Protected by** | `ProviderRegistryTest.productionRefusesMocks` + call from `Application.module` |

### What was wrong

`ProviderRegistry.requireEnvironmentSatisfied` existed, was correct, and had
its own passing test suite. It was never called.

```kotlin
fun Application.module(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    providerRegistry: ProviderRegistry = ProviderRegistry.mocks(),  // <- default
) {
    configurePlugins(settings)
    // the guard was missing here
}
```

`ProviderRegistry.mocks()` was the default, so a `production` deployment would
boot successfully and serve **fabricated academic data** to real students: a
timetable for a student who does not exist, attendance for classes nobody
attended. The failure is silent, and its worst outcome is a student being
marked absent from a class they attended.

### Why the tests did not catch it

`ProviderRegistryTest` called `requireEnvironmentSatisfied` directly and
passed. The function worked. What was missing was one call site.

This is the structural weakness of unit testing a pure function: it can verify
the rule but not that the rule is enforced. The test was green and the
protection was zero.

### Fix

The guard moved into `Application.module`, before plugins are installed:

```kotlin
fun Application.module(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    providerRegistry: ProviderRegistry = ProviderRegistry.mocks(),
) {
    // Fails the boot rather than serving fabricated academic data to real users.
    ProviderRegistry.requireEnvironmentSatisfied(providerRegistry, settings.environment)
    configurePlugins(settings)
    ...
}
```

### Lesson

**A security control that is not called from the boot path does not exist.**

Any guard that protects production must be wired into startup, not only
covered by a test of itself. When adding a check, the review question is not
"is this function tested?" but "what happens if nobody calls it?"

The boot-time smoke check in the [release readiness](RELEASE_READINESS.md)
checklist exists because of this bug.

---

## BUG-002 — `apiCall` wrapped `Outcome` in `Outcome`

| | |
|---|---|
| **Severity** | High |
| **Status** | Fixed |
| **Found by** | `ApiCallTest` (3 failures) |
| **Protected by** | `ApiCallTest` (6 tests) |

### What was wrong

```kotlin
suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())          // <- always a Success
} catch (e: ...) { ... }
```

`apiCall` was written to wrap a raw value. But the functions it wraps
(`requireBody`, `requireUnit`) already return an `Outcome`.

So every HTTP failure became `Outcome.Success(Outcome.Failure(AppError.Http(500)))`.

The outer layer said success. A screen that checked the outer result would
render a success state and then discover there is no data, producing a blank
screen with no error and no retry — for every server error, permanently.

### Why it survived compilation

`Outcome<Outcome<T>>` is a perfectly valid type. Nothing in Kotlin flags
`Outcome<T>` where `T` is itself an `Outcome`. It type-checked, it reviewed
cleanly, and it was wrong.

### Fix

`apiCall` now takes a block that already returns an `Outcome` and only
translates thrown exceptions:

```kotlin
suspend fun <T> apiCall(block: suspend () -> Outcome<T>): Outcome<T> = try {
    block()
} catch (e: ResponseException) { Outcome.Failure(e.toAppError()) }
  catch (e: java.io.IOException) { Outcome.Failure(AppError.Network(e.message)) }
  ...
```

The signature makes the nesting impossible: there is no way to return
`Success(Failure(...))` because the block must already be an `Outcome`.

### Lesson

**A generic type parameter will happily nest inside itself.**

When a helper claims to normalise a result, its signature should make misuse
unrepresentable. The fix is in the type, not in the call sites.

A second, smaller issue surfaced from the same test: Ktor raises its own
`ContentConvertException` for a JSON mismatch, which is not a kotlinx
`SerializationException`, so malformed bodies were reported as
`AppError.Unknown`. Now caught explicitly.

---

## BUG-003 — JUnit 5 tests compiled, then Android reported zero

| | |
|---|---|
| **Severity** | Critical |
| **Status** | Fixed |
| **Found by** | Comparing Gradle's "BUILD SUCCESSFUL" against the JUnit XML reports |
| **Protected by** | `useJUnitPlatform()` in all four Android modules, plus count verification |

### What was wrong

The Android modules used JUnit 5 (`junit-jupiter`). AGP's unit test task
defaults to the JUnit 4 runner.

The tests **compiled successfully**. The task **passed**. `BUILD SUCCESSFUL`.

They never ran. The runner found no JUnit 4 tests, discovered zero, executed
zero, and reported success. The suite produced no XML results at all.

The first symptom was a test count of zero alongside a green build, which is
the only reason this was caught at all.

### Why this is the worst of the three

A failing test is a nuisance. A test that does not run and reports success is
worse than having no test, because it manufactures confidence. Every future
developer would reasonably read a green Android build as "the Android tests
pass".

### Fix

```kotlin
// AGP defaults to the JUnit 4 runner, which silently reports zero tests for a
// JUnit 5 suite. Without this the tests compile and then never run.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Added to `core:common`, `core:ui`, `core:network` and `app`, with the comment
kept in place so nobody deletes it as boilerplate.

Immediately after the fix, 3 of 6 previously-never-executed `ApiCallTest` cases
failed, exposing BUG-002. The suite had been reporting success while
containing assertions that were false.

### Lesson

**A green build is not evidence that tests ran.**

The only reliable check is to count what was executed, from the reports:

```powershell
$files = Get-ChildItem -Recurse -Filter "TEST-*.xml" | Where-Object { $_.FullName -match "test-results" }
```

This is why the [test plan](TEST_PLAN.md) reports discovered, executed and
skipped separately, and why the CI workflow verifies the count rather than
trusting the exit code. `git diff --check` and a green build are necessary and
not sufficient.

---

## FIX-001 — `Student` and `Teacher` were the same type

Not a runtime defect. A modelling defect, corrected during the foundation and
recorded because the reasoning generalises.

### What was wrong

The academic model had a single `Person` type, used for students *and* for
teachers:

```kotlin
data class Teacher(val person: Person, val departmentCode: String? = null)
fun toPerson(student: ExternalStudent): Person   // a student mapped to Person
```

A `Person` for a student and a `Person` for a teacher were
indistinguishable. Nothing stopped passing a student's `Person` where a
teacher's was expected, and a commit message had already claimed a `Student`
model that did not exist.

The type system could not express "this is a student", so a mistake here would
have surfaced as a wrong name in a timetable rather than as a compile error.

### Fix

`Student` and `Teacher` are now distinct types, both wrapping a shared
`Person`:

```kotlin
data class Student(val person: Person)
data class Teacher(val person: Person, val departmentCode: String? = null)
```

`AcademicMappers.toPerson` became `toStudent`, returning `Student`. The
distinction is now enforced by the compiler.

### Lesson

**A model that erases a distinction the domain cares about will eventually
erase it in a bug.**

Students and teachers behave differently everywhere: permissions, what they
can see, what they can be marked as. Sharing a type meant the compiler had
nothing to say about any of it. `Person` still exists and still holds identity,
but role-specific data lives on the role type.

This is the same reasoning behind the value-class identifiers in
`shared/models`, and behind `IntegrationException` categories. Encode a
distinction once, at a boundary, and let the compiler carry it.

---

## BUG-004 — the CI whitespace check inspected nothing

| | |
|---|---|
| **Severity** | Medium |
| **Status** | Fixed |
| **Found by** | Running the CI command by hand against a commit that contained the defect |
| **Protected by** | `infrastructure/scripts/check-whitespace-test.sh` (9 cases, 15 assertions) |

### What was wrong

CI ran:

```bash
git diff --check
```

With no range, `git diff` compares the working tree to the index. On a clean CI
checkout the working tree *is* the index, so there is no diff to inspect, so
`--check` has nothing to report and exits 0.

The check was green on every run and had never once examined a committed line.

### Proof

A commit with trailing whitespace on an added line was pushed to the exact
state CI sees. Running the CI command by hand on that checkout:

```
$ git diff --check
$ echo $?
0
```

No output, exit 0 — the same result CI was getting on a commit that
demonstrably contained the problem.

### Why the correct fix is not `git diff --check` either

The intuitive patch is `git diff --check origin/main...HEAD`. That fixes
`pull_request`, but the same workflow also runs on `push`, where the merge base
is the wrong reference and three-dot ranges resolve against a fork-tracking
branch that may not exist.

The range depends on the event. Deriving it inside the script from
`GITHUB_EVENT_NAME` and `GITHUB_EVENT_BEFORE`, with explicit fallbacks, is the
only version that is correct for every trigger rather than for the one being
debugged.

### Fix

`infrastructure/scripts/check-whitespace.sh` resolves the range by event and
runs `git diff --check` against it. Both workflows check out full history
(`fetch-depth: 0`), without which no range resolution can work at all.

`check-whitespace-test.sh` builds throwaway repositories with deliberately
broken commits and asserts the script **fails** on each one, covering the
first commit, a new branch, a normal push, a pull request, and a clean
repository that must pass.

### Lesson

**A check that cannot fail is worse than no check, because it is read as
evidence.**

The command was plausible, the step was named correctly, and it had presumably
never failed because it had never done anything. Verification of a check means
watching it fail on a known-bad input, which is what the harness is for.

---

## BUG-005 — `gradlew clean` did not clean

| | |
|---|---|
| **Severity** | High |
| **Status** | Fixed |
| **Found by** | A from-scratch build finishing in 2 seconds |
| **Protected by** | Verifying module build directories are gone after `clean` |

### What was wrong

The root `clean` task deleted only the root project's build directory:

```kotlin
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
```

Every one of the eleven module build directories survived. Module-level build
outputs are what Gradle's up-to-date checks consult, so `clean build`
immediately afterwards skipped nearly all the work:

```
BUILD SUCCESSFUL in 2s
635 actionable tasks: 7 executed, 616 up-to-date
```

### Why it matters more than it looks

This is the same class of defect as the three above, one level up. Every
"built from clean" claim in this repository — including the ones in these
documents — was resting on a clean that did not clean. A stale output directory
can hide a broken incremental path, and the verification value of
"clean, then build" collapses to nothing.

Nothing was actually broken in the product code. The evidence was.

### Fix

```kotlin
// Deletes every module's build directory, not just the root one.
//
// BUG-005: this used to delete only rootProject's build directory, so
// `gradlew clean build` left all eleven module build directories intact and
// Gradle reported most tasks as up-to-date. A clean build reported as a clean
// build was not one, which is the same failure as BUG-003 in a different place:
// the exit code was fine and the evidence behind it was not.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
```

After the fix a from-scratch build runs 286 tasks with 0 module build
directories remaining, and the total figures in the
[test plan](TEST_PLAN.md) were recomputed from that run rather than inherited.

Note that on Windows two `core:network` jar files can survive while the Gradle
daemon holds them open. That is file locking, not a broken clean; it resolves
once the daemon is stopped.

### Lesson

**"Clean" is a claim about state, so it needs verifying like any other claim.**

The habit that caught this was noticing that a build was too fast to be real.
A second useful habit is to check that the thing you deleted is actually gone:

```powershell
Get-ChildItem -Recurse -Directory -Filter build | Where-Object { $_.FullName -match "(apps|services|shared)\\" }
```

This is the general form of the lesson from BUG-003: measure the artefact the
check is supposed to produce, not the exit code of the step that claims to
produce it.

---

## Summary

| ID | Severity | Found by | Now protected by |
|---|---|---|---|
| BUG-001 | Critical | Code review | Boot-path call + registry test + `ApplicationModuleBootTest` |
| BUG-002 | High | Failing test | `ApiCallTest` (20) |
| BUG-003 | Critical | Report count check | `useJUnitPlatform()` in 4 modules |
| BUG-004 | Medium | Running the CI command by hand | `check-whitespace-test.sh` (9 cases) |
| BUG-005 | High | A 2-second "clean" build | `clean` deletes all module dirs |
| FIX-001 | Modelling | Type review | Distinct `Student` / `Teacher` types |

All five bugs share one property: **the build was green throughout.** Two were
found by tests once the tests actually ran, one by reading the boot path, one by
running a CI command manually, and one by noticing a build was too fast to be
true. None was found by a passing build.

## Related

- [Test plan](TEST_PLAN.md)
- [Release readiness](RELEASE_READINESS.md)
- [Security model](../security/SECURITY_MODEL.md)
