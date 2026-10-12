# V14 high-contrast black — scope and verification

- High contrast on V8 Student/Teacher shells now uses a deep-black backdrop with no decorative campus image.
- Updates the effective Material color scheme for black background, white text, opaque matte cards and clear outlines.
- Reuses `LocalCompaneroHighContrast` in existing glass components; does not fork screens or make the login theme less accessible.
- Pure Kotlin contrast-token tests are included.

**This is not yet a full light-theme fix.** V8 still uses fixed dark text colors in many custom composables; flipping the student scaffold to light without migrating those tokens would reduce legibility. Light/system appearance through all V8 screens needs its own audited component/token migration and device screenshots. This change intentionally avoids claiming that goal.
