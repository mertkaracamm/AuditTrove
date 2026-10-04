#!/usr/bin/env node
// Yük testi: N sanal kullanıcı aynı anda (ya da belirli aralıkla) aynı PDF'i staging'e yükler.
// Ölçülen: başarı/başarısızlık, süre dağılımı (min / medyan / p95 / maks), skorların aynı kalıp kalmadığı.
// Gerçek LLM çağrısı yapar, gerçek para harcar: kısa belgede kullanıcı başına ~0,03 $ OpenAI + ikinciller.
//
// Kullanım:  node tools/load-test.mjs --file ./test-docs/02-konut-kira-sozlesmesi-tr.pdf --users 10
//            [--lang en] [--type rental] [--ramp 0]   (ramp: kullanıcılar arası başlama aralığı, ms)

import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { randomUUID } from 'node:crypto';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, arr) => a.startsWith('--') ? [a.slice(2), arr[i + 1]] : []).filter(Boolean));
const BASE = args.base || 'https://audittrove-staging-production.up.railway.app';
const FILE = args.file;
const USERS = Number(args.users || 10);
const LANG = args.lang || 'en';
const DOC_TYPE = args.type || 'general';
const RAMP_MS = Number(args.ramp || 0);
const POLL_MS = 3000;
const MAX_WAIT_MS = 10 * 60 * 1000;

if (!FILE) { console.error('--file gerekli'); process.exit(2); }
const bytes = readFileSync(FILE);

async function registerDevice() {
  const res = await fetch(`${BASE}/api/v1/devices`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId: randomUUID() }),
  });
  if (!res.ok) throw new Error(`cihaz kaydı ${res.status}: ${await res.text()}`);
  return (await res.json()).token;
}

// Tek sanal kullanıcı: kayıt → yükleme → bitene kadar sorgu. Her aşamanın süresi ayrı tutulur.
async function virtualUser(index) {
  const t0 = Date.now();
  const out = { index, ok: false, error: '', uploadMs: 0, totalMs: 0, score: null, findings: null, status: '' };
  try {
    const token = await registerDevice();
    const fd = new FormData();
    fd.append('language', LANG);
    fd.append('documentType', DOC_TYPE);
    fd.append('file', new Blob([bytes], { type: 'application/pdf' }), basename(FILE));
    const res = await fetch(`${BASE}/api/v1/audit/async`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd });
    out.uploadMs = Date.now() - t0;
    if (res.status !== 202 && res.status !== 200) throw new Error(`yükleme ${res.status}: ${(await res.text()).slice(0, 160)}`);
    const { id } = await res.json();
    while (Date.now() - t0 < MAX_WAIT_MS) {
      await new Promise((r) => setTimeout(r, POLL_MS));
      let poll;
      try {
        poll = await fetch(`${BASE}/api/v1/audit/jobs/${id}`, { headers: { Authorization: `Bearer ${token}` } });
      } catch { continue; }
      if (poll.status === 404) throw new Error('iş kayboldu (404)');
      const data = await poll.json();
      out.status = data.status;
      if (data.status === 'DONE') {
        out.ok = true;
        out.score = data.result?.riskScore ?? null;
        out.findings = (data.result?.risks || []).length;
        break;
      }
      if (data.status === 'FAILED') throw new Error(`iş başarısız: ${data.error}`);
    }
    if (!out.ok && !out.error) out.error = `${MAX_WAIT_MS / 60000} dakikada bitmedi`;
  } catch (e) {
    out.error = e.message;
  }
  out.totalMs = Date.now() - t0;
  return out;
}

function pct(sorted, p) {
  if (!sorted.length) return 0;
  const i = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, i)];
}
const sec = (ms) => `${(ms / 1000).toFixed(1)}s`;

(async () => {
  console.log(`hedef: ${BASE}`);
  console.log(`belge: ${basename(FILE)} (${(bytes.length / 1024).toFixed(0)} KB) | kullanıcı: ${USERS} | dil: ${LANG} | tür: ${DOC_TYPE} | aralık: ${RAMP_MS} ms`);
  console.log('başlıyor...\n');
  const started = Date.now();
  const tasks = [];
  for (let i = 0; i < USERS; i++) {
    tasks.push(virtualUser(i + 1));
    if (RAMP_MS > 0 && i < USERS - 1) await new Promise((r) => setTimeout(r, RAMP_MS));
  }
  const results = await Promise.all(tasks);
  const wall = Date.now() - started;

  const pad = (s, n) => String(s).padEnd(n);
  console.log(`${pad('kullanıcı', 10)} ${pad('yükleme', 9)} ${pad('toplam', 9)} ${pad('skor', 5)} ${pad('bulgu', 6)} durum`);
  for (const r of results.sort((a, b) => a.index - b.index)) {
    console.log(`${pad(r.index, 10)} ${pad(sec(r.uploadMs), 9)} ${pad(sec(r.totalMs), 9)} ${pad(r.score ?? '-', 5)} ${pad(r.findings ?? '-', 6)} ${r.ok ? 'OK' : 'HATA: ' + r.error}`);
  }

  const okOnes = results.filter((r) => r.ok);
  const times = okOnes.map((r) => r.totalMs).sort((a, b) => a - b);
  const scores = new Set(okOnes.map((r) => r.score));
  console.log('\nÖZET');
  console.log(`  başarı: ${okOnes.length}/${USERS}  |  toplam duvar süresi: ${sec(wall)}`);
  if (times.length) {
    console.log(`  süre: min ${sec(times[0])}  medyan ${sec(pct(times, 50))}  p95 ${sec(pct(times, 95))}  maks ${sec(times[times.length - 1])}`);
    console.log(`  verim: dakikada ${(okOnes.length / (wall / 60000)).toFixed(1)} inceleme`);
  }
  console.log(`  skor tutarlılığı: ${scores.size === 1 ? `hepsi ${[...scores][0]}` : 'FARKLI → ' + [...scores].join(' / ')}`);
  const errors = results.filter((r) => !r.ok).map((r) => r.error.replace(/\d+/g, '#'));
  if (errors.length) {
    const counts = errors.reduce((m, e) => m.set(e, (m.get(e) || 0) + 1), new Map());
    console.log('  hatalar:');
    for (const [e, n] of counts) console.log(`    ${n}× ${e}`);
  }
  process.exit(okOnes.length === USERS && scores.size <= 1 ? 0 : 1);
})();
