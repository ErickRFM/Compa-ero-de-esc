// Execute only in Vercel's protected build environment. No secret output.
import pg from 'pg';
import {databaseConnectionOptions} from './src/infrastructure/database/connection-options.ts';
import {APPLICATION_ROLE_QUERY, assertRoleInspection} from './src/infrastructure/database/role-policy.ts';
import {academicDeliveryTarget} from './src/integrations/companion/outbox/signed-transport.ts';
const institutionId = '0690309f-8897-4c45-bf7f-d46c9c3a8ae1';
let pool;
let stage = 'flags';
let failed = false;
try {
  if (process.env.SIII_ACADEMIC_OUTBOX_ENABLED !== 'true' ||
      !['false','true'].includes(process.env.SIII_COMPANION_DELIVERY_ENABLED)) throw 0;
  stage = 'destination';
  const targets = JSON.parse(process.env.SIII_COMPANION_DELIVERY_TARGETS_JSON || '{}');
  if (Object.keys(targets).length !== 1 || !Object.hasOwn(targets,institutionId)) throw 0;
  const target = academicDeliveryTarget(institutionId,process.env.SIII_COMPANION_DELIVERY_TARGETS_JSON);
  try {
    if (target.keyId !== 'siii-hmac-20261025-138dedb2ba5840759868ff77844616a7' ||
        target.url !== 'https://compa-ero-de-esc.onrender.com/integration/v1/events' ||
        process.env.SIII_PUBLIC_ORIGIN !== 'https://siii-three.vercel.app') throw 0;
  } finally { target.secret.fill(0); }
  stage = 'runtime_role';
  pool = new pg.Pool({...databaseConnectionOptions(process.env.DATABASE_URL,process.env.SIII_DATABASE_CA),max:1,connectionTimeoutMillis:10000,statement_timeout:15000});
  assertRoleInspection((await pool.query(APPLICATION_ROLE_QUERY)).rows[0]);
  stage = 'schema';
  await pool.query('SELECT 1 FROM academic_section_outbox LIMIT 0');
  await pool.query('SELECT 1 FROM academic_section_versions LIMIT 0');
  console.log('SIII_ACTIVATION_PREFLIGHT_OK=' + JSON.stringify({runtimeRoleSafe:true,schemaReady:true,targetVerified:true,academicOutboxEnabled:true,companionDeliveryEnabled:process.env.SIII_COMPANION_DELIVERY_ENABLED==='true'}));
} catch {
  console.log('SIII_ACTIVATION_PREFLIGHT_FAILED=' + stage);
  failed = true;
} finally { await pool?.end(); }
if (failed) process.exit(1);
