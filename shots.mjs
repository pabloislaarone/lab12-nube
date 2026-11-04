import { chromium } from 'playwright';
import fs from 'node:fs';
const out = process.argv[2];
const B = 'http://localhost:8080';
const browser = await chromium.launch();
async function session(email, password) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 860 }, deviceScaleFactor: 2, colorScheme: 'dark' });
  const page = await ctx.newPage();
  await page.goto(B + '/login');
  await page.fill('#email', email); await page.fill('#password', password);
  await page.click('button:has-text("Ingresar") >> nth=-1');
  await page.waitForURL('**/partidos'); await page.waitForTimeout(1200);
  return page;
}
const shot = async (p, name, full = false) => { await p.waitForTimeout(1000); await p.screenshot({ path: `${out}/${name}.png`, fullPage: full }); console.log(name); };
// Login
{ const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2, colorScheme: 'dark' }); const p = await ctx.newPage(); await p.goto(B + '/login'); await shot(p, '00-login'); }
// Participante
const u = await session('demo01@marcador.pe', 'demo123');
await shot(u, '01-partidos');
// pronosticar uno
try { const card = u.locator('article.match').filter({ has: u.locator('button:has-text("Guardar")') }).first();
  await card.locator('button[aria-label^="Sumar gol"]').first().click(); await card.locator('button[aria-label^="Sumar gol"]').first().click();
  await card.locator('button:has-text("Guardar")').click(); await u.waitForTimeout(600); await card.screenshot({ path: `${out}/02-pronostico-guardado.png` }); } catch (e) { console.log('pronostico', e.message); }
await u.click('text=Finalizados'); await shot(u, '03-finalizados');
await u.goto(B + '/salas'); await shot(u, '04-salas');
await u.click('a.room-card >> nth=0'); await shot(u, '05-sala-ranking');
await u.click('button:has-text("Miembros")'); await shot(u, '06-sala-miembros');
await u.goto(B + '/unirse/DEMO2026'); await shot(u, '07-invitacion');
await u.goto(B + '/ranking'); await shot(u, '08-ranking-global');
await u.goto(B + '/estadisticas'); await u.waitForSelector('.insight', { timeout: 15000 }).catch(() => {}); await shot(u, '09-estadisticas', true);
await u.goto(B + '/reglas'); await shot(u, '10-reglas', true);
// Admin
const a = await session('admin@marcador.pe', 'admin123');
await a.goto(B + '/admin'); await shot(a, '11-admin-resultados');
await a.click('button:has-text("Herramientas")'); await shot(a, '12-admin-bitacora-worker');
// RabbitMQ
try { const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2 }); const r = await ctx.newPage();
  await r.goto('http://localhost:15672/'); await r.fill('input[name=username]', 'marcador'); await r.fill('input[name=password]', 'marcador'); await r.click('input[type=submit]');
  await r.waitForTimeout(2500); await r.goto('http://localhost:15672/#/queues'); await r.waitForTimeout(3000); await shot(r, '13-rabbitmq-cola');
  await r.goto('http://localhost:15672/#/queues/%2F/scoring.recompute.queue'); await r.waitForTimeout(3000); await shot(r, '14-rabbitmq-consumidores', true);
} catch (e) { console.log('rabbit', e.message); }
// Terminales
const term = async (file, name) => {
  const txt = fs.readFileSync(`${out}/${file}`, 'utf8').replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const ctx = await browser.newContext({ viewport: { width: 1180, height: 200 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
  await p.setContent(`<html><body style="margin:0;background:#1e1e1e"><div style="padding:14px 18px;background:#2d2d2d;color:#bbb;font:13px system-ui">● ● ●  &nbsp; terminal — lab12-nube</div><pre style="margin:0;padding:18px;color:#e6e6e6;font:14px/1.5 'DejaVu Sans Mono',monospace;white-space:pre-wrap">${txt.replace(/^(\$.*)$/gm, '<span style="color:#c6f35b">$1</span>')}</pre></body></html>`);
  await p.screenshot({ path: `${out}/${name}.png`, fullPage: true }); console.log(name);
};
await term('compose-ps.txt', '20-docker-compose-ps');
await term('balanceo.txt', '21-balanceo');
await term('escalar5.txt', '22-escalar-5-replicas');
await term('worker.txt', '23-worker-replicas');
await term('stats.txt', '24-docker-stats');
await browser.close();
