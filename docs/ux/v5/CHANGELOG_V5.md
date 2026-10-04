# V5 change log

## 2026-10-04

Baseline main: `50441b8dc805840a430dfeac142c2f94adfc1041`.

Integration branch: `feat/ui-v5-adaptive-teacher`.

### Reconciled

- preserved deployed Render API as Android default;
- retained the existing V5 design-system, navigation and adaptive work.

### Brand

- added the official UPTlax logo as a reusable vector asset;
- added `UptlaxBrand`;
- replaced visible institutional `UPTx` labels in Login/Profile/catalog with UPTlax branding.

### First access

- added a first-access/registration entry from Login;
- registration validates matrícula/ID with institutional credentials through the existing `IdentityProvider`;
- the app does not persist the institution password;
- successful validation uses the server-issued platform session and provider-derived role/identity.

### Safety

No client-side attendance verification authority was added or changed.
