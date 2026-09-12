#!/usr/bin/env node
// Rapor sözleşmesi denetimi: verilen PDF'leri staging'e yükler, sonucu alır ve ekranda görülebilecek
// her alanı kurala karşı kontrol eder. Ekrana bakmak yerine bu tablo okunur.
//
// Kullanım:  node tools/check-report.mjs --base https://audittrove-staging-production.up.railway.app \
//                --docs ./test-docs --langs tr,en --runs 2
// Node 18+ (fetch/FormData/Blob yerleşik). Belgeler saklanmaz; script yalnızca yükler ve JSON'u okur.

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, basename } from 'node:path';
import { randomUUID } from 'node:crypto';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, arr) => a.startsWith('--') ? [a.slice(2), arr[i + 1]] : []).filter(Boolean));
const BASE = args.base || 'https://audittrove-staging-production.up.railway.app';
const DOCS = args.docs || './test-docs';
const LANGS = (args.langs || 'tr,en').split(',');
const RUNS = Number(args.runs || 2);
const DOC_TYPE = args.type || 'general';

const SCORES = new Set([12, 22, 36, 47, 60, 72, 82, 88, 95]);
const SEVERITIES = new Set(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);
const TR_WORDS = new Set(['ve', 'ile', 'için', 'bir', 'bu', 'olarak', 'olan', 'olup', 'tarihi', 'itibarıyla', 'itibariyle', 'göre', 'ancak', 'veya', 'ise', 'kadar', 'üzere', 'gibi', 'daha']);
const EN_WORDS = new Set(['the', 'and', 'of', 'to', 'in', 'for', 'with', 'is', 'are', 'by', 'from', 'that', 'as', 'which', 'this', 'or', 'has', 'have', 'not']);
const ENGINE_TITLE = /(declined by|increased by) \d|oranında (düştü|arttı)$/;
const INCREASE = /artı[şs]|artm[ıi][şs]|increase|rose|higher|yüksel/i;
const DECREASE = /azal|düş|decreas|declin|fell|lower|geriled/i;

function detectLang(text) {
  if (!text) return null;
  let tr = 0, en = 0, words = 0;
  for (const w of text.toLowerCase().split(/[^\p{L}]+/u)) {
    if (!w) continue;
    words++;
    if (TR_WORDS.has(w)) tr++;
    if (EN_WORDS.has(w)) en++;
  }
  // Küçük harfli Türkçe harfler İngilizce cümlede geçmez; "TÜİK" gibi kısaltmalar sayılmaz.
  if ((text.match(/[ğşıçöü]/g) || []).length >= 2 && en < 3) return 'tr';
  if (words < 6 || Math.abs(tr - en) < 2) return null;
  return tr > en ? 'tr' : 'en';
}

function amounts(text) {
  // ölçek/para birimi izleyen tutarlar; yüzde ve yıl değil
  const out = [];
  const re = /(\d[\d.,]*)\s*(?:milyon|million|milyar|billion|thousand|bin|TRY|TL|₺)|(?:TRY|TL|₺)\s*(\d[\d.,]*)/gi;
  let m;
  while ((m = re.exec(text || '')) !== null) {
    const raw = (m[1] || m[2]).replace(/[^\d]/g, '');
    if (raw) out.push(Number(raw));
  }
  return out;
}

async function registerDevice() {
  const res = await fetch(`${BASE}/api/v1/devices`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId: randomUUID() }),
  });
  if (!res.ok) throw new Error(`cihaz kaydı ${res.status}: ${await res.text()}`);
  return (await res.json()).token;
}

// Ağ kopması (fetch failed) test sonucu değildir; yükleme iki kez denenir.
async function runAudit(token, file, lang) {
  try {
    return await runAuditOnce(token, file, lang);
  } catch (e) {
    if (!/fetch failed|ECONN|ETIMEDOUT|socket/i.test(e.message)) throw e;
    await new Promise((r) => setTimeout(r, 5000));
    return await runAuditOnce(token, file, lang);
  }
}

async function runAuditOnce(token, file, lang) {
  const fd = new FormData();
  fd.append('language', lang);
  fd.append('documentType', DOC_TYPE);
  fd.append('file', new Blob([readFileSync(file)], { type: 'application/pdf' }), basename(file));
  const started = Date.now();
  const res = await fetch(`${BASE}/api/v1/audit/async`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd });
  if (res.status !== 202 && res.status !== 200) throw new Error(`yükleme ${res.status}: ${await res.text()}`);
  const { id } = await res.json();
  for (let i = 0; i < 90; i++) {
    await new Promise((r) => setTimeout(r, 4000));
    let poll;
    try {
      poll = await fetch(`${BASE}/api/v1/audit/jobs/${id}`, { headers: { Authorization: `Bearer ${token}` } });
    } catch (e) {
      continue; // geçici ağ hatası → sonraki tur
    }
    if (poll.status === 404) throw new Error('iş kayboldu (404) — sunucu yeniden başladı mı?');
    const data = await poll.json();
    if (data.status === 'DONE') return { result: data.result, ms: Date.now() - started };
    if (data.status === 'FAILED') throw new Error(`iş başarısız: ${data.error}`);
  }
  throw new Error('6 dakikada bitmedi');
}

function check(result, lang) {
  const fails = [];
  const f = (rule, detail) => fails.push(`${rule}: ${detail}`);
  if (!result) { f('sonuç', 'boş'); return fails; }

  // dil
  if (result.language !== lang) f('dil', `language=${result.language}, istenen ${lang}`);
  const texts = [['summary', result.summary], ['scoreRationale', result.scoreRationale]];
  (result.risks || []).forEach((r, i) => { texts.push([`risk[${i + 1}].title`, r.title], [`risk[${i + 1}].evidence`, r.evidence]); });
  (result.recommendations || []).forEach((t, i) => texts.push([`recommendation[${i + 1}]`, t]));
  (result.advisorQuestions || []).forEach((t, i) => texts.push([`question[${i + 1}]`, t]));
  for (const [name, t] of texts) {
    const d = detectLang(t);
    if (d && d !== lang) f('dil', `${name} ${d} görünüyor: "${String(t).slice(0, 110)}"`);
    if (/\[REPORT PAGE|\((Page|Sayfa|Rapor Sayfa|Report Page)\s*\d/i.test(t || '')) f('işaretçi', `${name} içinde sayfa atfı: "${String(t).slice(0, 140)}"`);
  }
  if (!result.summary || result.summary.length < 40) f('özet', 'boş ya da çok kısa');

  // skor
  const risks = result.risks || [];
  // Skora giren bulgular: motor + rubrik. LLM'in serbest gözlemleri (source=model) ve çapraz kontrol eklemeleri girmez.
  const counted = risks.filter((r) => r.source !== 'model' && !/^(Cross-check:|Çapraz doğrulama:)/.test(r.title || ''));
  const rank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
  const top = Math.max(0, ...counted.map((r) => rank[r.severity] || 0));
  const few = counted.length <= 3;
  const expected = { 4: few ? 22 : 12, 3: few ? 47 : 36, 2: few ? 72 : 60, 1: few ? 88 : 82, 0: 95 }[top];
  if (!SCORES.has(result.riskScore)) f('skor', `${result.riskScore} tanımlı 9 değerden değil`);
  else if (result.riskScore !== expected) f('skor', `${result.riskScore}, bulgulara göre ${expected} olmalıydı`);

  // bulgular
  const pageCount = result.pageCount || 0;
  risks.forEach((r, i) => {
    const tag = `risk[${i + 1}]`;
    if (!r.title) f('bulgu', `${tag} başlık yok`);
    if (!SEVERITIES.has(r.severity)) f('bulgu', `${tag} severity=${r.severity}`);
    if (!r.evidence) f('bulgu', `${tag} kanıt yok`);
    if (!Array.isArray(r.pages) || r.pages.length === 0) f('sayfa', `${tag} sayfası yok`);
    else if (pageCount && r.pages.some((p) => p < 1 || p > pageCount)) f('sayfa', `${tag} sayfa ${r.pages} aralık dışı (1-${pageCount})`);
    const big = (r.evidence || '').match(/\d[\d.,]{3,}\d/g) || [];
    const words = (r.evidence || '').match(/\p{L}{2,}/gu) || [];
    if (big.length >= 2 && words.length < 3 * big.length) f('kanıt', `${tag} ham tablo satırı gibi`);
    const text = `${r.title} ${r.evidence}`;
    if (INCREASE.test(text) && !DECREASE.test(text)) {
      const a = amounts(r.evidence);
      if (a.length >= 2 && a[1] < a[0]) f('yön', `${tag} "artış" diyor, tutarlar ${a[0]} → ${a[1]}`);
    }
  });

  // göstergeler
  const metrics = result.keyMetrics || [];
  if (metrics.length > 6) f('gösterge', `${metrics.length} kart (>6)`);
  const seen = new Set();
  metrics.forEach((m, i) => {
    const tag = `metric[${i + 1}] "${m.label}"`;
    if (!m.value) f('gösterge', `${tag} değer yok`);
    if (/\d/.test(m.value || '') && /\p{L}{3,}/u.test(m.value || '') && !/^\d{1,2}\s\p{L}+\s\d{4}$/u.test(m.value)) f('gösterge', `${tag} değerde birim gömülü: "${m.value}"`);
    const key = `${(m.label || '').toLowerCase()}|${(m.value || '').replace(/\D/g, '')}`;
    if (seen.has(key)) f('gösterge', `${tag} tekrar`);
    seen.add(key);
  });

  // referanslar = bulgu sayfaları
  const refPages = new Set((result.references || []).map((r) => Number((r.source || '').match(/\d+/)?.[0])).filter(Boolean));
  const riskPages = new Set(risks.flatMap((r) => r.pages || []));
  if (refPages.size && riskPages.size) {
    for (const p of riskPages) if (!refPages.has(p)) f('referans', `bulgu sayfası ${p} referans listesinde yok`);
    for (const p of refPages) if (!riskPages.has(p)) f('referans', `referans ${p} hiçbir bulguya bağlı değil`);
  }
  return fails;
}

function engineFindings(result) {
  return (result.risks || []).filter((r) => ENGINE_TITLE.test(r.title || '')).map((r) => `${r.title} | ${r.evidence} | p${(r.pages || []).join(',')}`);
}

const pad = (s, n) => String(s).padEnd(n);
(async () => {
  const files = readdirSync(DOCS).filter((n) => n.toLowerCase().endsWith('.pdf')).map((n) => join(DOCS, n)).filter((p) => statSync(p).isFile());
  if (!files.length) { console.error(`PDF yok: ${DOCS}`); process.exit(2); }
  const token = await registerDevice();
  let totalFail = 0;
  console.log(`${pad('belge', 44)} ${pad('dil', 4)} ${pad('koşu', 5)} ${pad('skor', 5)} ${pad('bulgu', 6)} ${pad('süre', 6)} durum`);
  for (const file of files) {
    const perLang = {};
    for (const lang of LANGS) {
      const runs = [];
      for (let k = 1; k <= RUNS; k++) {
        let row;
        try {
          const { result, ms } = await runAudit(token, file, lang);
          const fails = check(result, lang);
          row = { result, fails, ms };
        } catch (e) {
          row = { result: null, fails: [`çalıştırma: ${e.message}`], ms: 0 };
        }
        runs.push(row);
        const r = row.result;
        console.log(`${pad(basename(file).slice(0, 43), 44)} ${pad(lang, 4)} ${pad(k, 5)} ${pad(r ? r.riskScore : '-', 5)} ${pad(r ? (r.risks || []).length : '-', 6)} ${pad(row.ms ? Math.round(row.ms / 1000) + 's' : '-', 6)} ${row.fails.length ? 'FAIL' : 'PASS'}`);
        for (const x of row.fails) console.log(`    - ${x}`);
        totalFail += row.fails.length;
      }
      // koşular arası tutarlılık
      const ok = runs.filter((x) => x.result);
      if (ok.length >= 2) {
        const scores = new Set(ok.map((x) => x.result.riskScore));
        if (scores.size > 1) { console.log(`    - tutarlılık: skor koşular arasında değişti ${[...scores].join(' / ')}`); totalFail++; }
        const eng = ok.map((x) => engineFindings(x.result).join('\n'));
        if (new Set(eng).size > 1) {
          console.log('    - tutarlılık: motor bulguları koşular arasında farklı');
          eng.forEach((e, i) => console.log(`        koşu ${i + 1}: ${e.replace(/\n/g, ' || ') || '(yok)'}`));
          totalFail++;
        }
        const counts = ok.map((x) => (x.result.risks || []).filter((r) => r.source !== 'model').length);
        if (Math.max(...counts) - Math.min(...counts) > 1) { console.log(`    - tutarlılık: skora giren bulgu sayısı ${counts.join(' / ')} (1'den fazla oynadı)`); totalFail++; }
        const rubricTitles = ok.map((x) => (x.result.risks || []).filter((r) => r.source === 'rubric').map((r) => r.title).sort().join('|'));
        if (new Set(rubricTitles).size > 1) {
          console.log('    - tutarlılık: kontrol listesi bulguları koşular arasında farklı');
          rubricTitles.forEach((e, i) => console.log(`        koşu ${i + 1}: ${e.split('|').join(' | ')}`));
          totalFail++;
        }
      }
      if (ok.length) perLang[lang] = { score: ok[0].result.riskScore, counted: (ok[0].result.risks || []).filter((r) => r.source !== 'model').length };
    }
    // Dil bağımsızlığı: aynı belge iki dilde aynı skoru ve aynı sayıda skora giren bulguyu vermeli.
    const langs = Object.keys(perLang);
    if (langs.length >= 2) {
      const scores = new Set(langs.map((l) => perLang[l].score));
      if (scores.size > 1) { console.log(`    - dil bağımsızlığı: skor ${langs.map((l) => `${l}=${perLang[l].score}`).join(' / ')}`); totalFail++; }
      const counts = langs.map((l) => perLang[l].counted);
      if (Math.max(...counts) - Math.min(...counts) > 1) { console.log(`    - dil bağımsızlığı: skora giren bulgu ${langs.map((l) => `${l}=${perLang[l].counted}`).join(' / ')}`); totalFail++; }
    }
  }
  console.log(totalFail ? `\n${totalFail} FAIL` : '\nTÜMÜ PASS');
  process.exit(totalFail ? 1 : 0);
})();
