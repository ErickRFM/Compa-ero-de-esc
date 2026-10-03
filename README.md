# Compañero de Escuela

A school companion app: one place to see today's classes, attendance,
timetable and institutional notices, designed for the network and hardware
realities of a public school rather than for a campus with fibre.

The technical foundation is merged and the first student product slice is implemented: secure platform authentication, **Hoy**, **Agenda** and student-scoped offline academic cache. Real-school identity and academic providers are still required before a student pilot. See [Roadmap](docs/product/ROADMAP.md).

---

## Status

| Area | Status |
|---|---|
| Technical foundation | merged to `main` |
| Student product foundation | implemented in the product-foundation line |
| Android | Compose app with auth, Hoy, Agenda, profile/logout and encrypted session storage |
| API | Ktor auth + academic endpoints behind provider boundaries |
| Offline | Room snapshot cache scoped by authenticated student id |
| Real institutional identity | pending external provider integration |
| Real institutional schedule | pending external provider integration |
| Attendance / QR | intentionally follows academic-model stabilization |

## What is here

```
companero-de-escuela/
├── shared/                      # Pure Kotlin, no Android, no Ktor
│   ├── contracts/               # API wire types (ApiResponse, ApiError, health)
│   ├── models/                  # Academic domain (Student, Teacher, Schedule, ...)
│   └── validation/              # Config validation primitives
│
├── services/
│   └── api/                     # Ktor 3 backend
│       ├── config/              # Env-driven settings, fails fast
│       ├── database/            # MongoDB lifecycle behind an interface
│       ├── health/              # /health, /ready, /version
│       ├── plugins/             # Serialization, call id, CORS, StatusPages
│       └── integrations/        # Institution adapters + mappers + mocks
│
├── apps/android/
│   ├── app/                     # Single activity, Compose shell, Hilt
│   └── core/
│       ├── common/              # Outcome, AppError, DispatcherProvider
│       ├── designsystem/        # Material 3 theme, type scale, shapes
│       ├── ui/                  # ContentState, ContentStateHost
│       ├── navigation/          # Destinations, bottom bar, nav shell
│       ├── network/             # Ktor client, Outcome error translation
│       └── testing/             # TestDispatcherProvider
│
└── docs/                        # Architecture, security, privacy, quality
```

### Product modules now present

The repository creates modules only when they have real responsibility. The current student slice includes:

- `core:security` for encrypted platform-session storage and token inspection;
- `core:database` for student-scoped Room academic snapshots;
- `core:academic` as the canonical remote/cache academic repository;
- `feature:auth`, `feature:home` and `feature:schedule`.

Future modules such as attendance, notifications and optional location evidence are still created only when their product phase starts. See [ADR-001](docs/architecture/ADR-001-MODULE-BOUNDARIES.md).

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | Builds run on JDK 21 |
| Android SDK | compileSdk 36 | `minSdk 26`, `targetSdk 36` |
| Gradle | 8.14.3 | Via the wrapper; no local install needed |

Only the Gradle wrapper and an Android SDK are required. There is no database
to install: the API boots without one and reports itself as degraded.

---

## Build and test

The wrapper is the only supported entry point.

```bash
# Everything
./gradlew build

# Backend
./gradlew :services:api:test

# Android
./gradlew :apps:android:app:assembleDebug
./gradlew :apps:android:app:testDebugUnitTest
./gradlew lint
```

On Windows use `gradlew.bat`. To keep the log readable, a helper is included:

```bat
infrastructure\scripts\build.cmd build
```

It writes the full output to `%TEMP%\companero-build-logs` and prints the
path.

---

## Running the API

```bash
cp .env.example .env      # or: Copy-Item .env.example .env
./gradlew :services:api:run
```

| Variable | Required | Notes |
|---|---|---|
| `APP_ENV` | yes | `local` \| `development` \| `staging` \| `production` |
| `API_HOST` / `API_PORT` | no | Defaults `127.0.0.1` / `8080` |
| `MONGODB_DATABASE` | yes | Logical database name |
| `MONGODB_URI` | no | Blank boots without a database |
| `JWT_SECRET` | in production | Minimum 32 characters |

With no database configured the service still starts, which is deliberate:

- `GET /health` → `200`, status `up`, `mongodb` reported `degraded`
- `GET /ready` → `503`, status `down`
- `GET /version` → `200`

`/ready` returning 503 without a database is **correct behaviour**, not a
failure. It is how a load balancer learns the instance cannot serve traffic.
See [API architecture](docs/architecture/API_ARCHITECTURE.md).

`staging` and `production` refuse to start with mock providers configured, so
fabricated academic data can never reach a real user.

---

## Running the app

```bash
./gradlew :apps:android:app:installDebug
```

The debug build targets `http://10.0.2.2:8080/`, which is the host machine as
seen from the Android emulator. For a physical device, pass the machine's LAN
address via `ApiEnvironment.lan(address)` in
`apps/android/app/src/main/kotlin/org/companerodeescuela/di/NetworkModule.kt`.

---

## Architecture in one paragraph

Android never talks to an institutional system. It calls our API. The API
converts each institution's quirks inside an adapter, so the rest of the
system only ever sees our own domain types. Every adapter has a mock
implementation so the whole platform runs with no external dependency, and
those mocks are locked out of staging and production. The rules are in
[Integration architecture](docs/integrations/INTEGRATION_ARCHITECTURE.md).

---

## Documentation

| Area | Document |
|---|---|
| System | [System architecture](docs/architecture/SYSTEM_ARCHITECTURE.md) |
| Android | [Android architecture](docs/architecture/ANDROID_ARCHITECTURE.md) |
| API | [API architecture](docs/architecture/API_ARCHITECTURE.md) |
| Decisions | [ADR-001: module boundaries](docs/architecture/ADR-001-MODULE-BOUNDARIES.md) |
| Integrations | [Integration architecture](docs/integrations/INTEGRATION_ARCHITECTURE.md) |
| Product | [Vision](docs/product/PRODUCT_VISION.md) · [Roadmap](docs/product/ROADMAP.md) |
| UX | [Design system](docs/ux/DESIGN_SYSTEM.md) |
| Security | [Security model](docs/security/SECURITY_MODEL.md) · [Threat model](docs/security/THREAT_MODEL.md) |
| Privacy | [Location privacy](docs/privacy/LOCATION_PRIVACY.md) |
| Quality | [Test plan](docs/quality/TEST_PLAN.md) · [Bug register](docs/quality/BUG_REGISTER.md) · [Release readiness](docs/quality/RELEASE_READINESS.md) |

---

## Contributing

Small, coherent commits. No secrets, ever: `.env` is git-ignored and
`.env.example` documents every variable with the values left blank. Run
`./gradlew build` before pushing. CI runs the same tasks locally.
