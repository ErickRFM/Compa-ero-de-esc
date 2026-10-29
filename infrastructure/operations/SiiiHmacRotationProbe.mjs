// Execute only inside the protected SII deployment with the reviewed TS sender.
// This source contains no secret. A successful probe deliberately stops the build.
import { randomUUID } from 'node:crypto';
import { readProjectionStatus } from './src/integrations/companion/projection-status.ts';
import { academicDeliveryTarget } from './src/integrations/companion/outbox/signed-transport.ts';

const institutionId = '0690309f-8897-4c45-bf7f-d46c9c3a8ae1';
const keyId = 'siii-hmac-20261025-138dedb2ba5840759868ff77844616a7';
const receiverOrigin = 'https://compa-ero-de-esc.onrender.com';
const expectedSiiOrigin = 'https://siii-three.vercel.app';
let exitCode = 1;

try {
  if (process.env.SIII_ACADEMIC_OUTBOX_ENABLED !== 'false' ||
      process.env.SIII_COMPANION_DELIVERY_ENABLED !== 'false') {
    throw new Error();
  }
  const mode = process.env.SIII_HMAC_ROTATION_PROBE_MODE || 'staged';
  if (!['staged', 'active'].includes(mode)) throw new Error();
  const targets = mode === 'active'
    ? process.env.SIII_COMPANION_DELIVERY_TARGETS_JSON
    : process.env.SIII_COMPANION_DELIVERY_TARGETS_NEXT_JSON;
  const activeTargets = JSON.parse(process.env.SIII_COMPANION_DELIVERY_TARGETS_JSON || '{}');
  const activeIds = activeTargets && typeof activeTargets === 'object' && !Array.isArray(activeTargets)
    ? Object.keys(activeTargets) : [];
  const activeTargetSingleInstitutionMatchesExpected = activeIds.length === 1 && activeIds[0] === institutionId;
  const target = academicDeliveryTarget(institutionId, targets);
  try {
    if (target.keyId !== keyId || target.url !== receiverOrigin + '/integration/v1/events') {
      throw new Error();
    }
  } finally {
    target.secret.fill(0);
  }

  let validHttpStatus = null;
  let invalidHttpStatus = null;
  const reference = () => [{ eventId: randomUUID(), sectionId: randomUUID(), sourceVersion: 1 }];
  const valid = await readProjectionStatus(institutionId, reference(), {
    targets,
    http: async (url, init) => {
      const response = await fetch(url, init);
      validHttpStatus = response.status;
      return response;
    },
  });
  const invalid = await readProjectionStatus(institutionId, reference(), {
    targets,
    http: async (url, init) => {
      const headers = new Headers(init.headers);
      headers.set('X-Integration-Signature', 'v1=' + '0'.repeat(64));
      const response = await fetch(url, { ...init, headers });
      invalidHttpStatus = response.status;
      return response;
    },
  });

  let siiPublicOriginMatchesExpected = false;
  if (process.env.SIII_PUBLIC_ORIGIN) {
    const origin = new URL(process.env.SIII_PUBLIC_ORIGIN);
    siiPublicOriginMatchesExpected = origin.protocol === 'https:' &&
      !origin.username && !origin.password && !origin.search && !origin.hash &&
      !origin.port && origin.pathname === '/' && origin.origin === expectedSiiOrigin;
  }
  const passed = validHttpStatus === 200 && valid.status === 'verified' &&
    invalidHttpStatus === 401 && invalid.status === 'unavailable';
  console.log('SIII_HMAC_ROTATION_RESULT=' + JSON.stringify({
    mode, keyId, validHttpStatus, contractVerified: valid.status === 'verified',
    invalidHttpStatus, invalidRejected: invalid.status === 'unavailable',
    academicOutboxEnabled: false, companionDeliveryEnabled: false,
    siiPublicOriginMatchesExpected, activeTargetSingleInstitutionMatchesExpected,
    passed, applicationBuildSkipped: true,
  }));
  exitCode = passed ? 17 : 1;
} catch {
  console.log('SIII_HMAC_ROTATION_PROBE_GUARD_FAILED');
}
process.exit(exitCode);
