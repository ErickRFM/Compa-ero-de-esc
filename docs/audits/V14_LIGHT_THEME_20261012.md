# V14 — Theme parity, first major screen migration

- The V8 scaffold now respects light/system selection from the parent CompaneroTheme and preserves official dark V8 branding when dark is effective.
- High contrast is context-aware: black in dark mode; the parent's accessible light scheme in light mode.
- Glass panels, chips, student academic cards and high-frequency screens use MaterialTheme semantic surface/text colors instead of fixed white-on-dark values in light mode.
- Existing dark imagery and glass effects are preserved; the light mode avoids an unreadable dark campus image.
- Unit tests assert palette resolver semantics.

**Scope still requiring real acceptance:** screenshots at 360/390/430dp/large text and audits of every remaining component, including login, tertiary admin screens, and full English strings. Test all six role experiences on emulator and a physical device before marking institutional UX complete.

Base: `186497336d063e9edd468301cf8f66fd031d6487`.
