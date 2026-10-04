# Compañero de Clase — UPTlax UI V5

## Source of truth

This directory records the V5 visual baseline. The product is not being redesigned from scratch: the current implementation is refined against the approved UPTlax reference while preserving working auth, attendance, offline sync, CameraX/ML Kit and role-aware behavior.

## Rules

- UPTlax is the institutional brand. Do not reintroduce `UPTx` as branding.
- Crimson is brand/primary emphasis; semantic green, amber, blue and red keep their state meaning.
- Existing reusable V4/V5 components are evolved rather than duplicated.
- Presentation changes must not alter attendance authority: scan -> pending -> server decision.
- Android never calls the school system directly.
- Data shown in production must come from real contracts/providers, not mock copy embedded in screens.

## Current integration branch

`feat/ui-v5-adaptive-teacher`

The branch contains the current V5 candidate and is reviewed through PR #28.
