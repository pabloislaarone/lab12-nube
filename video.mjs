// Graba el video de demostración contra el sistema real (http://localhost:8080).
import { chromium } from 'playwright';
import fs from 'node:fs';

const OUT = process.argv[2];
const B = 'http://localhost:8080';
const W = 1280, H = 720;
const CODE = JSON.parse(fs.readFileSync(`${OUT}/prep.json`, 'utf8'));
fs.mkdirSync(`${OUT}/raw`, { recursive: true });
fs.mkdirSync(`${OUT}/stills`, { recursive: true });

const browser = await chromium.launch();
const ONLY_WORKER = process.argv.includes('--only-worker');

const CAP = `window.__cap = (t) => {
  let d = document.getElementById('__cap');
  if (!d) { d = document.createElement('div'); d.id = '__cap';
    d.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);max-width:1120px;background:rgba(5,8,6,.9);color:#fff;font:600 19px Inter,system-ui,sans-serif;padding:12px 22px;border-radius:12px;z-index:2147483647;box-shadow:0 8px 30px rgba(0,0,0,.45);border:1px solid rgba(198,243,91,.55);text-align:center;pointer-events:none;line-height:1.35';
    document.body.appendChild(d); }
  d.textContent = t; };`;

async function rec(name, token) {
  const ctx = await browser.newContext({
    viewport: { width: W, height: H }, colorScheme: 'dark',
    recordVideo: { dir: `${OUT}/raw/tmp-${name}`, size: { width: W, height: H } },
  });
  await ctx.addInitScript(CAP);
  if (token) await ctx.addInitScript((t) => localStorage.setItem('marcador.token', t), token);
  const page = await ctx.newPage();
  return { ctx, page, name };
}
async function done(r) {
  const v = r.page.video();
  await r.ctx.close();
  fs.renameSync(await v.path(), `${OUT}/raw/${r.name}.webm`);
  console.log('video', r.name);
}
const cap = (p, t) => p.evaluate((t) => window.__cap && window.__cap(t), t);
const wait = (p, ms) => p.waitForTimeout(ms);
async function go(p, path, caption) {
  await p.goto(B + path, { waitUntil: 'networkidle' });
  await wait(p, 400);
  if (caption) await cap(p, caption);
}
async function smoothScroll(p, y) {
  await p.evaluate((y) => window.scrollBy({ top: y, behavior: 'smooth' }), y);
  await wait(p, 900);
}

if (!ONLY_WORKER) {
// ---------------- Escena 1: participante pronostica ----------------
{
  const r = await rec('app1');
  const p = r.page;
  await go(p, '/login', 'Inicio de sesión: la API devuelve un JWT (sin sesión en el servidor)');
  await wait(p, 800);
  await p.locator('#email').pressSequentially('demo01@marcador.pe', { delay: 40 });
  await p.locator('#password').pressSequentially('demo123', { delay: 40 });
  await wait(p, 300);
  await p.click('form button.btn-primary');
  await p.waitForURL('**/partidos');
  await wait(p, 500);
  await cap(p, 'Partidos por jugar: el pronóstico se puede editar hasta el pitazo inicial');
  await wait(p, 3500);
  await cap(p, 'Abajo a la derecha se ve qué réplica de la API atendió la petición (balanceo)');
  await wait(p, 3000);
  let card = p.locator('article.match').filter({ has: p.locator('button:has-text("Guardar")') })
    .filter({ hasText: '+1 anticipado' }).first();
  if ((await card.count()) === 0) {
    card = p.locator('article.match').filter({ has: p.locator('button:has-text("Guardar")') }).first();
  }
  await card.scrollIntoViewIfNeeded();
  await cap(p, 'Registro un pronóstico con más de 24 h de anticipación: suma +1 si acierto');
  const plus = card.locator('button[aria-label^="Sumar gol"]');
  await wait(p, 800);
  await plus.nth(0).click(); await wait(p, 350);
  await plus.nth(0).click(); await wait(p, 350);
  await plus.nth(1).click(); await wait(p, 600);
  await card.locator('button:has-text("Guardar")').click();
  await wait(p, 2500);
  await p.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await wait(p, 700);
  await p.click('button[role="tab"]:has-text("Finalizados")');
  await wait(p, 600);
  await cap(p, 'Partidos finalizados: cada tarjeta muestra la regla aplicada y los puntos ganados');
  await wait(p, 4000);
  await smoothScroll(p, 330);
  await cap(p, 'Exacto = 5 · Ganador = 3 · +1 por anticipación · +2 por racha de 3 aciertos');
  await wait(p, 4000);
  await done(r);
}

// ---------------- Escena 2: crear sala ----------------
let roomCode = null;
let roomPath = '/ranking';
{
  const r = await rec('app2', CODE.token1);
  const p = r.page;
  await go(p, '/salas', 'Salas privadas: cada grupo tiene su propio ranking');
  await wait(p, 2500);
  await p.click('button:has-text("Crear sala")');
  await wait(p, 700);
  await cap(p, 'El participante crea una sala nueva');
  await p.locator('#room-name').pressSequentially('Salón 5B TECSUP', { delay: 45 });
  await wait(p, 500);
  await p.click('dialog button.btn-primary');
  await p.waitForURL(/\/salas\/\d+/);
  await wait(p, 900);
  roomCode = (await p.locator('.invite-code').textContent()).trim();
  roomPath = new URL(p.url()).pathname;
  await cap(p, `Se genera un código único de invitación (${roomCode}) y un enlace para compartir`);
  await wait(p, 4000);
  await done(r);
}

// ---------------- Escena 3: otro usuario se une ----------------
{
  const r = await rec('app3', CODE.token2);
  const p = r.page;
  await go(p, `/unirse/${roomCode}`, 'Otro participante abre el enlace de invitación');
  await wait(p, 3000);
  await p.click('button:has-text("Unirme a la sala")');
  await p.waitForURL(/\/salas\/\d+/);
  await wait(p, 700);
  await cap(p, 'Ya es miembro: aparece en el ranking de la sala');
  await wait(p, 3000);
  await p.click('button[role="tab"]:has-text("Miembros")');
  await wait(p, 500);
  await cap(p, 'Miembros de la sala (el dueño puede expulsar, regenerar el código o eliminarla)');
  await wait(p, 3500);
  await done(r);
}

// ---------------- Escena 4: ranking y estadísticas con IA ----------------
{
  const r = await rec('app4', CODE.token1);
  const p = r.page;
  await go(p, '/ranking', 'Área de participantes: ranking global con puntos, exactos, aciertos y racha');
  await wait(p, 4500);
  await go(p, '/estadisticas', 'Estadísticas del participante');
  await wait(p, 2000);
  await smoothScroll(p, 330);
  await cap(p, 'Estadísticas con IA: análisis personalizado (Claude/Gemini, o análisis automático sin clave)');
  await wait(p, 5000);
  await smoothScroll(p, 400);
  await cap(p, 'Cómo sumó puntos por regla y su evolución partido a partido');
  await wait(p, 3500);
  await done(r);
}

// ---------------- Escena 5: resultado → recálculo → ranking en vivo ----------------
{
  const live = await rec('live-ranking', CODE.token1);
  const t0 = Date.now();
  await go(live.page, roomPath, 'Sesión de Lucía: ranking de su sala abierto mientras el admin registra un resultado');
  await wait(live.page, 1500);

  const a = await rec('live-admin', CODE.tokenAdmin);
  const p = a.page;
  await go(p, '/admin', 'El administrador registra el resultado de un partido que ya se jugó');
  await wait(p, 3000);
  const row = p.locator('.admin-row').first();
  await row.locator('.goal-input').nth(0).fill('2'); await wait(p, 400);
  await row.locator('.goal-input').nth(1).fill('1'); await wait(p, 600);
  const clickAt = Date.now();
  await row.locator('button:has-text("Registrar")').click();
  await wait(p, 600);
  await cap(p, 'La API solo guarda el marcador y publica un evento en RabbitMQ: un worker recalcula');
  await wait(p, 3500);
  await done(a);

  await live.page.waitForSelector('[data-sonner-toast]', { timeout: 15000 }).catch(() => {});
  await cap(live.page, 'El ranking de la sala se actualizó solo (Server-Sent Events), sin recargar la página');
  await wait(live.page, 4500);
  fs.writeFileSync(`${OUT}/offsets.json`, JSON.stringify({ live: Math.max(0, (clickAt - t0) / 1000 - 0.3) }));
  await done(live);
}

// ---------------- Escena 6: bitácora del worker ----------------
{
  const r = await rec('bitacora', CODE.tokenAdmin);
  const p = r.page;
  await go(p, '/admin');
  await p.click('button[role="tab"]:has-text("Herramientas")');
  await wait(p, 800);
  await cap(p, 'Bitácora: qué réplica del worker procesó cada evento y cuánto tardó');
  await smoothScroll(p, 300);
  await wait(p, 4500);
  await done(r);
}

// ---------------- Escena 7: RabbitMQ ----------------
{
  const r = await rec('rabbit');
  const p = r.page;
  await p.goto('http://localhost:15672/');
  await p.fill('input[name=username]', 'marcador');
  await p.fill('input[name=password]', 'marcador');
  await p.click('input[type=submit]');
  await wait(p, 2500);
  await p.goto('http://localhost:15672/#/queues/%2F/scoring.recompute.queue');
  await wait(p, 2500);
  await cap(p, 'RabbitMQ: la cola de puntuación tiene 2 consumidores (las 2 réplicas del worker)');
  await wait(p, 5000);
  await done(r);
}

}

// ---------------- Imágenes fijas (portada, terminales, gráficos, cierre) ----------------
async function still(name, html) {
  const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  const p = await ctx.newPage();
  await p.setContent(`<html><head><style>
    body{margin:0;width:${W}px;height:${H}px;background:#0b100d;color:#ecf3ee;font-family:Inter,system-ui,sans-serif;overflow:hidden}
    .card{position:absolute;inset:0;display:flex;flex-direction:column;justify-content:center;padding:0 90px}
    .eyebrow{color:#c6f35b;font-weight:700;letter-spacing:.12em;text-transform:uppercase;font-size:16px;margin-bottom:14px}
    h1{font-size:64px;margin:0 0 12px;letter-spacing:-.03em} h2{font-size:30px;margin:0 0 18px;letter-spacing:-.02em}
    p{font-size:22px;color:#a7b6ac;margin:6px 0} .term{margin:0 50px;background:#1e1e1e;border-radius:12px;overflow:hidden;border:1px solid #333}
    .bar{background:#2d2d2d;color:#aaa;font:14px system-ui;padding:10px 16px} pre{margin:0;padding:18px 22px;color:#e6e6e6;font:17px/1.5 'DejaVu Sans Mono',monospace;white-space:pre-wrap}
    .cmd{color:#c6f35b} .cap{position:absolute;left:50%;bottom:24px;transform:translateX(-50%);background:rgba(5,8,6,.9);border:1px solid rgba(198,243,91,.55);padding:12px 22px;border-radius:12px;font-weight:600;font-size:19px;white-space:nowrap}
    img{max-width:1140px;max-height:470px;border-radius:12px;background:#fff;padding:12px}
  </style></head><body>${html}</body></html>`);
  await p.waitForTimeout(300);
  await p.screenshot({ path: `${OUT}/stills/${name}.png` });
  await ctx.close();
  console.log('still', name);
}
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const term = (file, title, caption) => {
  const txt = esc(fs.readFileSync(`${OUT}/${file}`, 'utf8')).replace(/^(\$.*)$/gm, '<span class="cmd">$1</span>');
  return `<div class="card" style="padding:0;justify-content:flex-start;padding-top:40px"><div class="eyebrow" style="margin-left:50px">${title}</div>
    <div class="term"><div class="bar">● ● ●&nbsp;&nbsp; terminal — lab12-nube</div><pre>${txt}</pre></div></div><div class="cap">${caption}</div>`;
};
const img = (path) => `data:image/png;base64,${fs.readFileSync(path).toString('base64')}`;

if (!ONLY_WORKER) {
await still('00-portada', `<div class="card"><div class="eyebrow">Laboratorio 12 · Desarrollo en la Nube · TECSUP</div>
  <h1>Marcador 26</h1><h2>Pronósticos del Mundial 2026 con Docker, balanceo de carga y escalamiento horizontal</h2>
  <p>Spring Boot · React · PostgreSQL · Redis · RabbitMQ · Nginx · k6</p><p style="margin-top:28px;color:#ecf3ee">Pablo Isla</p></div>`);
await still('01-compose', term('compose-ps.txt', 'Contenerización con Docker',
  'docker compose up: 9 contenedores sanos (3 API, 2 workers, gateway, BD, caché y cola)'));
await still('20-balanceo', term('balanceo.txt', 'Balanceo de carga con Nginx (least_conn)',
  '30 peticiones repartidas 10 / 10 / 10 entre las 3 réplicas de la API'));
await still('21-escalar', term('escalar5.txt', 'Escalamiento horizontal en caliente',
  'Se escala a 5 réplicas sin reiniciar: Nginx las detecta y reparte 10 a cada una'));

await still('30-estres1', `<div class="card" style="align-items:center;text-align:center"><div class="eyebrow">Prueba de estrés con k6 · escenario realista</div>
  <img src="${img(CODE.chart1)}"/></div><div class="cap">300 usuarios simultáneos: con 3 réplicas la latencia baja a la mitad, 0 % de errores</div>`);
await still('31-estres2', `<div class="card" style="align-items:center;text-align:center"><div class="eyebrow">Prueba de estrés con k6 · saturación</div>
  <img src="${img(CODE.chart2)}"/></div><div class="cap">Con 3 réplicas la p99 baja de 475 ms a 9 ms y no se descarta ninguna petición</div>`);
await still('99-cierre', `<div class="card"><div class="eyebrow">Resumen</div><h1 style="font-size:52px">Marcador 26</h1>
  <p>✔ Arquitectura con API sin estado, worker por eventos y datos compartidos</p>
  <p>✔ 5 reglas de puntuación · salas con invitación · ranking en vivo · estadísticas con IA</p>
  <p>✔ Docker Compose, balanceo con Nginx y escalamiento horizontal</p>
  <p>✔ 51 pruebas automáticas y pruebas de estrés 1 vs 3 réplicas</p>
  <p style="margin-top:24px;color:#ecf3ee">github.com/pabloislaarone/lab12-nube</p></div>`);

}

if (ONLY_WORKER) {
  await still('22-worker', term('worker.txt', 'Escalamiento del worker',
    'Los eventos se reparten entre las 2 réplicas del worker (competing consumers)'));
}

await browser.close();
