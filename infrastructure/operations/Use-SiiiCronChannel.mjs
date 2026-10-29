// Protected transfer of the existing cron secret. Never accepts or prints it.
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
const mode = process.argv[2];
const projectId = 'prj_5OMS0EPuwS3cuQF4ICqBYBF6UNlT';
const teamId = 'team_yJjgpau6x2SsEeKXBw09sE4a';
let stage = 'mode';
let secret;
let token;
let status = null;
try {
  if (!['provision','verify-disabled','verify-active'].includes(mode)) throw 0;
  stage = 'session';
  token = JSON.parse(readFileSync('C:/Users/erik5/AppData/Roaming/com.vercel.cli/Data/auth.json','utf8')).token;
  if (typeof token !== 'string' || !token) throw 0;
  async function api(path) {
    const response = await fetch('https://api.vercel.com'+path+'?teamId='+teamId,{headers:{Authorization:'Bearer '+token},redirect:'error',signal:AbortSignal.timeout(20000)});
    status = response.status;
    if (!response.ok) { await response.body?.cancel(); throw 0; }
    return response.json();
  }
  stage = 'project.verify';
  const project = await api('/v9/projects/'+projectId);
  if (project.id !== projectId || project.name !== 'siii' || project.accountId !== teamId) throw 0;
  stage = 'cron.read.protected';
  const env = await api('/v1/projects/'+projectId+'/env/fT4hGELiTw2HboNv');
  if (env.key !== 'CRON_SECRET' || !env.target?.includes('production') || typeof env.value !== 'string' || env.value.length < 16) throw 0;
  secret = env.value; env.value = null; token = null;
  if (mode === 'provision') {
    stage = 'github.secret.provision';
    const result = spawnSync('C:/Program Files/GitHub CLI/gh.exe',['secret','set','CRON_SECRET','--repo','ErickRFM/Siii'],{input:secret,encoding:'utf8',stdio:['pipe','pipe','pipe'],windowsHide:true,timeout:30000});
    if (result.status !== 0) throw 0;
    console.log(JSON.stringify({stage,status:'OK',existingSecretReused:true,secretPrinted:false}));
  } else {
    stage = 'delivery.authorized';
    const url = 'https://siii-three.vercel.app/api/internal/companion-snapshot-delivery';
    const response = await fetch(url,{headers:{Authorization:'Bearer '+secret},redirect:'error',signal:AbortSignal.timeout(110000)});
    status = response.status;
    const body = await response.json();
    const expected = mode === 'verify-active' ? 'processed' : 'disabled';
    if (status !== 200 || body.status !== expected || (expected==='processed' && body.disabled !== false)) throw 0;
    const summary = {at:new Date().toISOString(),httpStatus:status,status:body.status};
    for (const key of ['claimed','accepted','retried','failed','budgetExhausted','cancelled']) {
      if (typeof body[key] === 'number' || typeof body[key] === 'boolean') summary[key] = body[key];
    }
    stage = 'delivery.unauthorized';
    const denied = await fetch(url,{redirect:'error',signal:AbortSignal.timeout(25000)});
    summary.unauthorizedStatus = denied.status;
    await denied.body?.cancel();
    if (denied.status !== 401) throw 0;
    summary.passed = true;
    console.log(JSON.stringify(summary));
  }
} catch {
  console.log(JSON.stringify({stage,status:'FAILED',httpStatus:status,secretPrinted:false}));
  process.exitCode = 1;
} finally { secret = null; token = null; }
