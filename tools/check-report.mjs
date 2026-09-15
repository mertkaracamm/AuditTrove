#!/usr/bin/env node
// Rapor sözleşmesi denetimi: verilen PDF'leri staging'e yükler, sonucu alır ve ekranda görülebilecek
// her alanı kurala karşı kontrol eder. Ekrana bakmak yerine bu tablo okunur.
//
// Kullanım:  node tools/check-report.mjs --base https://audittrove-staging-production.up.railway.app \
//                --docs ./test-docs --langs tr,en --runs 2
// Node 18+ (fetch/FormData/Blob yerleşik). Belgeler saklanmaz; script yalnızca yükler ve JSON'u okur.

import { readdirSync, readFileSync, statSync, mkdirSync, writeFileSync } from 'node:fs';
import { join, basename } from 'node:path';
import { randomUUID } from 'node:crypto';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, arr) => a.startsWith('--') ? [a.slice(2), arr[i + 1]] : []).filter(Boolean));
const BASE = args.base || 'https://audittrove-staging-production.up.railway.app';
const DOCS = args.docs || './test-docs';
const LANGS = (args.langs || 'tr,en').split(',');
const RUNS = Number(args.runs || 2);
const DOC_TYPE = args.type || 'general';
// --dump <klasör>: her koşunun ham JSON'u yazılır (hata ayıklama için).
const DUMP = args.dump || null;

// Konum doğrulaması: pdf.js ile sayfadaki metin satırlarının yerleri bağımsız okunur; her çıpa kanıttaki
// sayının (yoksa kelimelerin) gerçekten geçtiği satıra oturmalı. PDFBox'tan bağımsız ikinci göz.
let pdfjs = null;
try { pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs'); } catch (e) { pdfjs = null; }
const NUM_TOKEN = /\d(?:[\d.,]|[ \u00a0](?=\d{3}(?!\d)))*\d|\d/g;
const digitKeys = (text) => new Set(((text || '').match(NUM_TOKEN) || []).map((t) => t.replace(/\D/g, '')).filter((k) => k.length >= 3 && !/^(19|20)\d\d$/.test(k)));
const wordKeys = (text) => new Set(((text || '').toLowerCase().match(/\p{L}{5,}/gu) || []).map((w) => w.slice(0, 5)));
// "%8", "8%", "%54,4": yüzdeler iki haneli olsa da ayırt edicidir.
const percentKeys = (text) => new Set([...(text || '').matchAll(/%\s?(\d{1,3}(?:[.,]\d+)?)|(\d{1,3}(?:[.,]\d+)?)\s?%/g)].map((m) => 'P' + (m[1] || m[2]).replace(/\D/g, '')));

async function pageLines(file) {
  if (!pdfjs) return null;
  const data = new Uint8Array(readFileSync(file));
  const doc = await pdfjs.getDocument({ data, useSystemFonts: true, disableFontFace: true, isEvalSupported: false }).promise;
  const pages = new Map();
  for (let n = 1; n <= doc.numPages; n++) {
    const page = await doc.getPage(n);
    const [x0, y0, x1, y1] = page.view;
    const W = x1 - x0, H = y1 - y0;
    const tc = await page.getTextContent();
    const items = tc.items.filter((it) => it.str && it.str.trim()).map((it) => {
      const [, , , , e, f] = it.transform;
      const h = it.height || Math.abs(it.transform[3]) || 8;
      return { text: it.str, x: (e - x0) / W, top: (y1 - (f + h)) / H, h: h / H, base: f };
    });
    items.sort((a, b) => b.base - a.base || a.x - b.x);
    const lines = [];
    for (const it of items) {
      const last = lines[lines.length - 1];
      if (last && Math.abs(last.base - it.base) < 2) { last.text += ' ' + it.text; last.top = Math.min(last.top, it.top); last.h = Math.max(last.h, it.h); }
      else lines.push({ text: it.text, top: it.top, h: it.h, base: it.base });
    }
    pages.set(n, lines);
  }
  return pages;
}

// Çıpa dikdörtgeninin kapsadığı satırlar kanıtla eşleşmeli; eşleşen satır başka yerdeyse kayma raporlanır.
const flatten = (t) => (t || '')
  .replace(/[\u2018\u2019]/g, "'").replace(/[\u201c\u201d]/g, '"')
  .replace(/[\u2013\u2014]/g, '-').replace(/\u00a0/g, ' ')
  .replace(/\s+/g, ' ').trim().toLowerCase();

// Alıntı belgeden kelimesi kelimesine alındığı için önce birebir aranır: satırlar birleştirilip
// bakılır, böylece satır sonuna denk gelen alıntı da bulunur. Kelime sayma yöntemi burada yanılıyordu —
// "The vehicle is sold" cümlesinde eşleşecek tek uzun kelime var, başlık satırı ise iki kelime
// tutturup kendini doğru yer sanıyordu.
function literalSpan(quote, pl) {
  const needle = flatten(quote);
  if (needle.length < 12) return null;
  let joined = '';
  const lineOf = [];
  pl.forEach((l, idx) => {
    const t = flatten(l.text);
    if (!t) return;
    if (joined) { joined += ' '; lineOf.push(idx); }
    for (let c = 0; c < t.length; c++) lineOf.push(idx);
    joined += t;
  });
  const at = joined.indexOf(needle);
  if (at < 0) return null;
  const end = Math.min(lineOf.length - 1, at + needle.length - 1);
  return { from: lineOf[at], to: lineOf[end] };
}

function checkAnchors(result, lines, f, note) {
  if (!lines) return;
  (result.risks || []).forEach((r, i) => {
    const tag = `risk[${i + 1}]`;
    // Kanıt rapor dilinde, belge kendi dilinde olabilir; kelimeler belge dilindeki alıntıdan (quote) alınır,
    // sayılar ve yüzdeler her ikisinden.
    const src = `${r.evidence || ''} ${r.quote || ''}`;
    const nums = new Set([...digitKeys(src), ...percentKeys(src)]);
    // Alıntı bazen rapor diline çevrilmiş gelir; iki kaynağın kelimeleri birlikte kullanılır.
    const words = new Set([...wordKeys(r.quote), ...wordKeys(r.evidence)]);
    const keysOf = (text) => new Set([...digitKeys(text), ...percentKeys(text)]);
    const matches = (text) => (nums.size ? [...keysOf(text)].some((k) => nums.has(k)) : false)
      || [...wordKeys(text)].filter((w) => words.has(w)).length >= 2;
    for (const a of r.anchors || []) {
      const pl = lines.get(a.page) || [];
      const span = literalSpan(r.quote, pl);
      for (const q of a.rects || []) {
        const covered = pl.filter((l) => l.top + l.h / 2 >= q.y - 0.002 && l.top + l.h / 2 <= q.y + q.h + 0.002);
        // Alıntı sayfada birebir bulunduysa doğrulama nettir: çıpa o satırlara değiyor mu?
        if (span) {
          const touches = pl.some((l, idx) => idx >= span.from && idx <= span.to
            && l.top + l.h >= q.y - 0.004 && l.top <= q.y + q.h + 0.004);
          if (touches) continue;
          const at = pl[span.from];
          f('konum', `${tag} çıpa y=${q.y.toFixed(3)} h=${q.h.toFixed(3)} alıntının yerine değmiyor; alıntı y=${at.top.toFixed(3)} "${at.text.slice(0, 50)}"`);
          continue;
        }
        const text = covered.map((l) => l.text).join(' ');
        if (matches(text)) continue;
        const hit = pl.find((l) => matches(l.text));
        // Kanıt sayfanın hiçbir satırında bulunamıyorsa çıpanın yanlış olduğunu söyleyemeyiz: elimizde
        // doğrulayacak malzeme yok. Rapor belgenin dilinden başka bir dildeyse ve alıntı kısaysa
        // (tek uzun kelime, sayı yok) eşleşme eşiği zaten tutmuyor. Bunu hata değil, doğrulanamadı say.
        if (!hit) {
          note('doğrulanamadı', `${tag} çıpa y=${q.y.toFixed(3)} kanıtla karşılaştırılamadı (alıntıda eşleşecek anahtar yok)`);
          continue;
        }
        f('konum', `${tag} çıpa y=${q.y.toFixed(3)} h=${q.h.toFixed(3)} kapsadığı metin "${text.slice(0, 50)}" kanıtla eşleşmiyor; kanıt satırı y=${hit.top.toFixed(3)} "${hit.text.slice(0, 50)}"`);
      }
    }
  });
}

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

function check(result, lang, lines) {
  const fails = [];
  const notes = [];
  const f = (rule, detail) => fails.push(`${rule}: ${detail}`);
  // Doğrulanamayan şeyler ayrı listede: koşuyu düşürmez ama ekranda görünür.
  const note = (rule, detail) => notes.push(`${rule}: ${detail}`);
  fails.notes = notes;
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
  // Sunucudaki ScoreScale ile aynı: bulgular sayılmaz, ağırlıkları toplanır. Tek bir düşük önemli
  // bulgu eşiği geçirmesin diye; iki yerde iki kural olursa hangisinin doğru olduğu belirsizleşir.
  const weight = counted.reduce((sum, r) => sum + (rank[r.severity] || 0), 0);
  const light = weight <= 4;
  const expected = { 4: light ? 22 : 12, 3: light ? 47 : 36, 2: light ? 72 : 60, 1: light ? 88 : 82, 0: 95 }[top];
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
    // konum: sayfası olan bulgunun çıpası olmalı; dikdörtgenler 0..1 içinde. Boyanamayan bulgu FAIL değil, sayılır.
    const anchors = Array.isArray(r.anchors) ? r.anchors : [];
    if (Array.isArray(r.pages) && r.pages.length && anchors.length === 0) f('konum', `${tag} sayfası var, çıpası yok`);
    for (const a of anchors) {
      if (!r.pages.includes(a.page)) f('konum', `${tag} çıpa sayfası ${a.page} bulgu sayfalarında yok`);
      for (const q of a.rects || []) {
        if ([q.x, q.y, q.w, q.h].some((v) => typeof v !== 'number' || v < 0 || v > 1) || q.x + q.w > 1.0001 || q.y + q.h > 1.0001 || q.w <= 0 || q.h <= 0) f('konum', `${tag} dikdörtgen sayfa dışı: ${JSON.stringify(q)}`);
      }
    }
    // Sayı kalıbı sunucudaki ReportGate.looksLikeRawRow ile birebir aynı olmalı; ayrı kalıp
    // kullanınca kapıdan geçen kanıt burada takılıyor ve hangisinin haklı olduğu belirsizleşiyor.
    const big = (r.evidence || '').match(/\d{1,3}(?:[ \u00a0]\d{3})+(?:[.,]\d+)?|\d[\d.,]{3,}\d/g) || [];
    const words = (r.evidence || '').match(/\p{L}{2,}/gu) || [];
    if (big.length >= 2 && words.length < 3 * big.length) f('kanıt', `${tag} ham tablo satırı gibi: "${(r.evidence || '').slice(0, 90)}"`);
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
  checkAnchors(result, lines, f, note);
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
  if (!pdfjs) console.log('not: pdfjs-dist yok, çıpa konumları doğrulanmadı (tools/ içinde npm install).');
  if (DUMP) mkdirSync(DUMP, { recursive: true });
  let totalFail = 0;
  console.log(`${pad('belge', 44)} ${pad('dil', 4)} ${pad('koşu', 5)} ${pad('skor', 5)} ${pad('bulgu', 6)} ${pad('boyalı', 7)} ${pad('süre', 6)} durum`);
  for (const file of files) {
    const perLang = {};
    let lines = null;
    try { lines = await pageLines(file); } catch (e) { console.log(`    - pdf.js okuyamadı: ${e.message}`); }
    for (const lang of LANGS) {
      const runs = [];
      for (let k = 1; k <= RUNS; k++) {
        let row;
        try {
          const { result, ms } = await runAudit(token, file, lang);
          if (DUMP) writeFileSync(join(DUMP, `${basename(file, '.pdf')}-${lang}-${k}.json`), JSON.stringify(result, null, 2));
          const fails = check(result, lang, lines);
          row = { result, fails, ms };
        } catch (e) {
          row = { result: null, fails: [`çalıştırma: ${e.message}`], ms: 0 };
        }
        runs.push(row);
        const r = row.result;
        const painted = r ? (r.risks || []).filter((x) => (x.anchors || []).some((a) => (a.rects || []).length)).length : 0;
        const withPages = r ? (r.risks || []).filter((x) => (x.pages || []).length).length : 0;
        console.log(`${pad(basename(file).slice(0, 43), 44)} ${pad(lang, 4)} ${pad(k, 5)} ${pad(r ? r.riskScore : '-', 5)} ${pad(r ? (r.risks || []).length : '-', 6)} ${pad(r ? `${painted}/${withPages}` : '-', 7)} ${pad(row.ms ? Math.round(row.ms / 1000) + 's' : '-', 6)} ${row.fails.length ? 'FAIL' : 'PASS'}`);
        for (const x of row.fails) console.log(`    - ${x}`);
        for (const x of (row.fails.notes || [])) console.log(`    ~ ${x}`);
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
        // Skor gerekçesi kodda yazılır: aynı bulgu dağılımı aynı cümleyi vermeli.
        // Bulgu sayısı ±1 oynaması kabul edilen gürültü; gerekçe yalnızca aynı şiddet dağılımında karşılaştırılır.
        const byHistogram = new Map();
        for (const x of ok) {
          const key = (x.result.risks || []).filter((r) => r.source !== 'model').map((r) => r.severity).sort().join(',');
          if (!byHistogram.has(key)) byHistogram.set(key, new Set());
          byHistogram.get(key).add(x.result.scoreRationale || '');
        }
        for (const [key, set] of byHistogram) {
          if (set.size > 1) {
            console.log(`    - tutarlılık: aynı bulgu dağılımında (${key}) skor gerekçesi farklı`);
            [...set].forEach((e, i) => console.log(`        ${i + 1}: ${e}`));
            totalFail++;
          }
        }
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
