// Prueba de estrés de la Polla Mundialista con k6.
// Simula participantes que miran partidos, pronostican, revisan su sala y el ranking.
//
// Variables:
//   BASE_URL     URL del balanceador (por defecto http://localhost:8080)
//   REPLICAS     etiqueta para el reporte (1, 3...)
//   PROFILE      stress (usuarios realistas), saturacion (sube la tasa hasta el limite) o smoke
//   RESULTS_DIR  carpeta donde se guarda el resumen (por defecto /results)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const REPLICAS = __ENV.REPLICAS || 'n';
const PROFILE = __ENV.PROFILE || 'stress';
const RESULTS_DIR = __ENV.RESULTS_DIR || '/results';
const DEMO_USERS = 40;

const predictionsSaved = new Counter('predicciones_guardadas');

const STAGES = {
  smoke: [
    { duration: '15s', target: 5 },
    { duration: '15s', target: 0 },
  ],
  stress: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 150 },
    { duration: '1m', target: 300 },
    { duration: '30s', target: 300 },
    { duration: '20s', target: 0 },
  ],
};

// "saturacion": tasa de peticiones creciente y sin pausas, para encontrar el punto de quiebre
const SCENARIOS = {
  saturacion: {
    executor: 'ramping-arrival-rate',
    startRate: 50,
    timeUnit: '1s',
    preAllocatedVUs: 300,
    maxVUs: 1500,
    stages: [
      { duration: '30s', target: 300 },
      { duration: '1m', target: 900 },
      { duration: '1m', target: 1800 },
      { duration: '30s', target: 1800 },
    ],
  },
  default: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: STAGES[PROFILE] || STAGES.stress,
    gracefulRampDown: '10s',
  },
};
const THINK = PROFILE !== 'saturacion';

export const options = {
  scenarios: {
    participantes: SCENARIOS[PROFILE] || SCENARIOS.default,
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<2000'],
    checks: ['rate>0.98'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

function login(email, password) {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password }), jsonHeaders());
  return res.status === 200 ? res.json('token') : null;
}

// Se ejecuta una sola vez: prepara datos demo y obtiene tokens
export function setup() {
  const admin = login('admin@polla.pe', 'admin123');
  if (!admin) throw new Error('No se pudo iniciar sesión como admin');

  const demo = http.post(`${BASE}/api/admin/demo`, JSON.stringify({ users: DEMO_USERS }), jsonHeaders(admin));
  if (demo.status !== 200) throw new Error(`No se pudieron crear los datos demo: ${demo.status}`);

  const tokens = [];
  for (let i = 1; i <= DEMO_USERS; i++) {
    const token = login(`demo${String(i).padStart(2, '0')}@polla.pe`, 'demo123');
    if (token) tokens.push(token);
  }

  const matches = http.get(`${BASE}/api/matches`).json();
  const openMatches = matches.filter((m) => !m.locked).map((m) => m.id);

  const rooms = http.get(`${BASE}/api/rooms`, jsonHeaders(tokens[0])).json();
  const roomId = rooms.length ? rooms[0].id : null;

  return { tokens, openMatches, roomId };
}

export default function (data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  const auth = jsonHeaders(token);
  const roll = Math.random();

  if (roll < 0.35) {
    // Ver la lista de partidos con mis pronósticos
    const res = http.get(`${BASE}/api/matches`, Object.assign({ tags: { name: 'GET /api/matches' } }, auth));
    check(res, { 'partidos 200': (r) => r.status === 200 });
  } else if (roll < 0.6) {
    // Ver el ranking global
    const res = http.get(`${BASE}/api/ranking`, Object.assign({ tags: { name: 'GET /api/ranking' } }, auth));
    check(res, { 'ranking 200': (r) => r.status === 200 });
  } else if (roll < 0.8 && data.openMatches.length) {
    // Pronosticar un partido abierto
    const matchId = data.openMatches[Math.floor(Math.random() * data.openMatches.length)];
    const body = JSON.stringify({
      homeGoals: Math.floor(Math.random() * 4),
      awayGoals: Math.floor(Math.random() * 4),
    });
    const res = http.put(`${BASE}/api/matches/${matchId}/prediction`, body,
      Object.assign({ tags: { name: 'PUT /api/matches/:id/prediction' } }, auth));
    if (check(res, { 'pronóstico 200': (r) => r.status === 200 })) predictionsSaved.add(1);
  } else if (roll < 0.93 && data.roomId) {
    // Ver la sala y su ranking
    const res = http.get(`${BASE}/api/rooms/${data.roomId}/ranking`,
      Object.assign({ tags: { name: 'GET /api/rooms/:id/ranking' } }, auth));
    check(res, { 'ranking sala 200': (r) => r.status === 200 });
  } else {
    // Mis estadísticas
    const res = http.get(`${BASE}/api/stats/me`, Object.assign({ tags: { name: 'GET /api/stats/me' } }, auth));
    check(res, { 'estadísticas 200': (r) => r.status === 200 });
  }

  // Tiempo de "lectura" de una persona real (no aplica en saturacion)
  if (THINK) sleep(0.3 + Math.random() * 0.7);
}

function fmt(n, digits = 1) {
  return n === undefined || n === null ? '-' : Number(n).toFixed(digits);
}

// Resumen propio: JSON completo + tabla en Markdown
export function handleSummary(data) {
  const m = data.metrics;
  const d = m.http_req_duration.values;
  const row = {
    replicas: REPLICAS,
    perfil: PROFILE,
    peticiones: m.http_reqs.values.count,
    rps: m.http_reqs.values.rate,
    lat_avg_ms: d.avg,
    lat_med_ms: d.med,
    lat_p90_ms: d['p(90)'],
    lat_p95_ms: d['p(95)'],
    lat_p99_ms: d['p(99)'],
    lat_max_ms: d.max,
    errores_pct: m.http_req_failed.values.rate * 100,
    checks_ok_pct: m.checks ? m.checks.values.rate * 100 : null,
    vus_max: m.vus_max ? m.vus_max.values.max : null,
    predicciones: m.predicciones_guardadas ? m.predicciones_guardadas.values.count : 0,
    iteraciones_descartadas: m.dropped_iterations ? m.dropped_iterations.values.count : 0,
  };

  const md = [
    `### Escenario "${PROFILE}" con ${REPLICAS} réplica(s) de la API`,
    '',
    '| Métrica | Valor |',
    '|---|---|',
    `| Usuarios virtuales (máx.) | ${row.vus_max} |`,
    `| Peticiones totales | ${row.peticiones} |`,
    `| Peticiones por segundo | ${fmt(row.rps)} |`,
    `| Latencia promedio | ${fmt(row.lat_avg_ms)} ms |`,
    `| Latencia mediana | ${fmt(row.lat_med_ms)} ms |`,
    `| Latencia p95 | ${fmt(row.lat_p95_ms)} ms |`,
    `| Latencia p99 | ${fmt(row.lat_p99_ms)} ms |`,
    `| Latencia máxima | ${fmt(row.lat_max_ms)} ms |`,
    `| Errores | ${fmt(row.errores_pct, 2)} % |`,
    `| Checks correctos | ${fmt(row.checks_ok_pct, 2)} % |`,
    `| Pronósticos guardados | ${row.predicciones} |`,
    `| Iteraciones descartadas (k6 no alcanzó a enviarlas) | ${row.iteraciones_descartadas} |`,
    '',
  ].join('\n');

  const out = { stdout: `\n${md}\n` };
  out[`${RESULTS_DIR}/k6-${PROFILE}-${REPLICAS}-replicas.json`] = JSON.stringify(row, null, 2);
  out[`${RESULTS_DIR}/k6-${PROFILE}-${REPLICAS}-replicas.md`] = md;
  return out;
}
