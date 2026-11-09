# Resend delivery webhook — Compañero

## Endpoint

`POST /webhooks/resend`

The endpoint accepts only raw JSON requests with valid Svix headers. It rejects missing/invalid signatures, timestamps outside the five-minute replay window, and bodies larger than 32 KiB. It returns:

- `200` for a valid event (including duplicates).
- `401` for a missing, stale, or invalid signature.
- `400` for a signed but malformed event.
- `413` for a body larger than 32 KiB.
- `503` until both MongoDB and `RESEND_WEBHOOK_SECRET` are configured.

## Resend configuration

Create one webhook for the QA endpoint and a separate webhook for production. Subscribe only to:

- `email.sent`
- `email.delivered`
- `email.delivery_delayed`
- `email.bounced`
- `email.failed`
- `email.suppressed`
- `email.complained`

Use these confirmed Render endpoints:

- QA: `https://companero-qa-univ-v25.onrender.com/webhooks/resend`
- Production: `https://compa-ero-de-esc.onrender.com/webhooks/resend`

Do not point QA events at production or share QA and production signing secrets.

Copy the `signing_secret` returned by Resend (prefix `whsec_`) into the matching Render service as `RESEND_WEBHOOK_SECRET`. The application validates that it decodes to 32 bytes and never prints it. Restart/redeploy the service after setting the variable.

There are currently no Resend webhooks configured for the connected account. Both Render public URLs are now confirmed. Create the QA webhook after the QA code is deployed; create the production webhook only after the production route is deployed.

## Event correlation and persistence

Invitation email requests carry the Resend tags `category=companero_invitation` and `invitation_id=<opaque invitation ID>`. Resend includes tags in email webhook payloads, allowing delivery events to update the matching invitation without correlating by recipient address.

The API stores minimal records in `platform_audit_events`, keyed by `svix-id`. Duplicate deliveries are idempotent. The audit record contains event type, opaque resource ID, provider email ID and timestamps; it does not store the raw payload, recipient address, invitation code, API key or signing secret.

Invitation delivery metadata is updated only when the incoming event timestamp is newer than the last recorded event. Event states include ACCEPTED, DELIVERED, DELAYED, BOUNCED, FAILED, SUPPRESSED and COMPLAINED. Provider acceptance is not represented as delivery.

## Rollout checklist

- [ ] Deploy this code to QA.
- [ ] Add QA `RESEND_WEBHOOK_SECRET` to Render and create the QA webhook.
- [ ] Send a test invitation to a controlled mailbox and confirm `email.sent` then `email.delivered`.
- [ ] Replay the same webhook event and confirm one audit row only.
- [ ] Send to a controlled invalid/suppressed test address and verify bounce/suppression handling.
- [ ] Confirm the invitation code and raw recipient never appear in logs or audit rows.
- [ ] Repeat independently for production after QA is certified.
