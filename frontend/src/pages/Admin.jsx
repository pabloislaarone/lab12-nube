import { useState } from 'react';
import { toast } from 'sonner';
import { api } from '../api.js';
import { ErrorBox, Skeletons, TeamBadge } from '../components/ui.jsx';
import { formatKickoff, formatShort } from '../format.js';
import { useFetch, useNow, useRankingUpdates } from '../hooks.js';

function toLocalInput(iso) {
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function ResultRow({ match, onDone }) {
  const [home, setHome] = useState(match.homeGoals ?? '');
  const [away, setAway] = useState(match.awayGoals ?? '');
  const [busy, setBusy] = useState(false);
  const finished = match.status === 'FINISHED';

  async function save() {
    setBusy(true);
    try {
      await api(`/admin/matches/${match.id}/result`, {
        method: 'PUT',
        body: { homeGoals: Number(home), awayGoals: Number(away) },
      });
      toast.success(finished ? 'Resultado corregido' : 'Resultado registrado', {
        description: 'El worker está recalculando los puntajes.',
      });
      onDone();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-row">
      <div className="admin-match">
        <span className="row" style={{ gap: 8 }}>
          <TeamBadge code={match.homeCode} />
          <strong>
            {match.homeTeam} vs {match.awayTeam}
          </strong>
        </span>
        <span className="subtle" style={{ fontSize: 12.5 }}>
          {formatKickoff(match.kickoffAt)} · {match.predictionsCount} pronósticos
        </span>
      </div>
      <div className="admin-actions">
        <input className="goal-input" type="number" min="0" max="30" value={home} onChange={(e) => setHome(e.target.value)} aria-label={`Goles ${match.homeTeam}`} />
        <span className="subtle">–</span>
        <input className="goal-input" type="number" min="0" max="30" value={away} onChange={(e) => setAway(e.target.value)} aria-label={`Goles ${match.awayTeam}`} />
        <button className={`btn btn-sm ${finished ? '' : 'btn-primary'}`} onClick={save} disabled={busy || home === '' || away === ''}>
          {finished ? 'Corregir' : 'Registrar'}
        </button>
      </div>
    </div>
  );
}

function ScheduleRow({ match, onDone }) {
  const [kickoff, setKickoff] = useState(toLocalInput(match.kickoffAt));
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    try {
      await api(`/admin/matches/${match.id}`, {
        method: 'PUT',
        body: {
          stage: match.stage,
          homeTeam: match.homeTeam,
          awayTeam: match.awayTeam,
          homeCode: match.homeCode,
          awayCode: match.awayCode,
          venue: match.venue,
          kickoffAt: new Date(kickoff).toISOString(),
        },
      });
      toast.success('Fecha actualizada');
      onDone();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-row">
      <div className="admin-match">
        <strong>
          {match.homeTeam} vs {match.awayTeam}
        </strong>
        <span className="subtle" style={{ fontSize: 12.5 }}>
          {match.stage} · {match.predictionsCount} pronósticos
        </span>
      </div>
      <div className="admin-actions">
        <input className="input" style={{ width: 210, height: 34 }} type="datetime-local" value={kickoff} onChange={(e) => setKickoff(e.target.value)} aria-label="Fecha y hora" />
        <button className="btn btn-sm" onClick={save} disabled={busy || kickoff === toLocalInput(match.kickoffAt)}>
          Reprogramar
        </button>
      </div>
    </div>
  );
}

const EMPTY_MATCH = { stage: 'Grupo A', homeTeam: '', homeCode: '', awayTeam: '', awayCode: '', venue: '', kickoffAt: '' };

function CreateMatch({ onDone }) {
  const [form, setForm] = useState(EMPTY_MATCH);
  const [busy, setBusy] = useState(false);
  const update = (f) => (e) => setForm((s) => ({ ...s, [f]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await api('/admin/matches', {
        method: 'POST',
        body: { ...form, kickoffAt: new Date(form.kickoffAt).toISOString() },
      });
      toast.success('Partido programado');
      setForm(EMPTY_MATCH);
      onDone();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card card-pad" onSubmit={submit}>
      <div className="grid-2">
        <div className="field">
          <label>Local</label>
          <input className="input" value={form.homeTeam} onChange={update('homeTeam')} required placeholder="Perú" />
        </div>
        <div className="field">
          <label>Código local</label>
          <input className="input input-code" value={form.homeCode} onChange={update('homeCode')} required maxLength={3} minLength={3} placeholder="PER" />
        </div>
        <div className="field">
          <label>Visitante</label>
          <input className="input" value={form.awayTeam} onChange={update('awayTeam')} required placeholder="Chile" />
        </div>
        <div className="field">
          <label>Código visitante</label>
          <input className="input input-code" value={form.awayCode} onChange={update('awayCode')} required maxLength={3} minLength={3} placeholder="CHI" />
        </div>
        <div className="field">
          <label>Fase</label>
          <input className="input" value={form.stage} onChange={update('stage')} required />
        </div>
        <div className="field">
          <label>Estadio</label>
          <input className="input" value={form.venue} onChange={update('venue')} placeholder="Estadio Nacional, Lima" />
        </div>
        <div className="field">
          <label>Fecha y hora</label>
          <input className="input" type="datetime-local" value={form.kickoffAt} onChange={update('kickoffAt')} required />
        </div>
      </div>
      <button className="btn btn-primary" disabled={busy}>
        Programar partido
      </button>
    </form>
  );
}

function Tools({ onDone }) {
  const runs = useFetch('/admin/scoring-runs');
  const [users, setUsers] = useState(20);
  const [busy, setBusy] = useState(null);
  useRankingUpdates(() => runs.reload(true));

  async function demo() {
    setBusy('demo');
    try {
      const r = await api('/admin/demo', { method: 'POST', body: { users: Number(users) } });
      toast.success('Datos demo listos', {
        description: `${r.users} participantes · ${r.finishedMatches} resultados · sala ${r.roomCode}`,
      });
      onDone();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(null);
    }
  }

  async function recompute() {
    setBusy('recompute');
    try {
      await api('/admin/recompute', { method: 'POST' });
      toast.success('Recálculo encolado en RabbitMQ');
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="two-col" style={{ marginTop: 0 }}>
        <div className="card card-pad">
          <h3 style={{ fontSize: 16, marginBottom: 6 }}>Simulador de demo</h3>
          <p className="muted" style={{ margin: '0 0 14px', fontSize: 13.5 }}>
            Crea participantes (<code>demoNN@marcador.pe</code> / <code>demo123</code>) con pronósticos anticipados, normales, de último minuto y faltantes. Carga resultados de los partidos que ya empezaron y arma la sala <code>DEMO2026</code>.
          </p>
          <div className="row">
            <input className="input" style={{ width: 90 }} type="number" min="2" max="200" value={users} onChange={(e) => setUsers(e.target.value)} aria-label="Cantidad de participantes" />
            <button className="btn btn-primary" onClick={demo} disabled={busy !== null}>
              <span className="btn-label" data-busy={busy === 'demo'}>Generar datos demo</span>
            </button>
          </div>
        </div>
        <div className="card card-pad">
          <h3 style={{ fontSize: 16, marginBottom: 6 }}>Recalcular puntajes</h3>
          <p className="muted" style={{ margin: '0 0 14px', fontSize: 13.5 }}>
            Publica un evento en RabbitMQ. Una de las réplicas del worker lo toma y recalcula todo; el resultado es el mismo aunque se repita.
          </p>
          <button className="btn" onClick={recompute} disabled={busy !== null}>
            <span className="btn-label" data-busy={busy === 'recompute'}>Encolar recálculo</span>
          </button>
        </div>
      </div>

      <h2 className="section-title">Bitácora del worker</h2>
      {runs.loading && !runs.data ? (
        <Skeletons count={1} height={200} className="" />
      ) : runs.error ? (
        <ErrorBox error={runs.error} onRetry={runs.reload} />
      ) : (
        <div className="card table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Motivo</th>
                <th>Réplica del worker</th>
                <th className="right">Usuarios</th>
                <th className="right">Partidos</th>
                <th className="right">Duración</th>
              </tr>
            </thead>
            <tbody>
              {runs.data.length === 0 && (
                <tr>
                  <td colSpan={6} className="subtle">
                    Todavía no hay recálculos.
                  </td>
                </tr>
              )}
              {runs.data.map((r) => (
                <tr key={r.id}>
                  <td>{formatShort(r.createdAt)}</td>
                  <td>
                    <span className="chip">{r.reason}</span>
                  </td>
                  <td className="replica">{r.instance}</td>
                  <td className="right num">{r.usersProcessed}</td>
                  <td className="right num">{r.matchesProcessed}</td>
                  <td className="right num">{r.durationMs} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

const TABS = [
  { id: 'results', label: 'Resultados' },
  { id: 'schedule', label: 'Partidos' },
  { id: 'tools', label: 'Herramientas' },
];

export default function Admin() {
  const { data, loading, error, reload } = useFetch('/matches');
  const [tab, setTab] = useState('results');
  const now = useNow(30_000);
  useRankingUpdates(() => reload(true));

  const list = data ?? [];
  const pending = list.filter((m) => m.status === 'SCHEDULED' && new Date(m.kickoffAt).getTime() <= now);
  const finished = list.filter((m) => m.status === 'FINISHED').reverse();
  const upcoming = list.filter((m) => m.status === 'SCHEDULED' && new Date(m.kickoffAt).getTime() > now);

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Administración</div>
          <h1>Panel del administrador</h1>
          <p>Carga resultados, programa partidos y prepara datos para demostraciones o pruebas de carga.</p>
        </div>
        <div className="tabs" role="tablist">
          {TABS.map((t) => (
            <button key={t.id} role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'tools' ? (
        <Tools onDone={() => reload(true)} />
      ) : loading && !data ? (
        <Skeletons count={1} height={300} className="" />
      ) : error ? (
        <ErrorBox error={error} onRetry={reload} />
      ) : tab === 'results' ? (
        <>
          <h2 className="section-title" style={{ marginTop: 0 }}>
            Esperando resultado <span className="count">{pending.length}</span>
          </h2>
          <div className="card">
            {pending.length === 0 ? (
              <p className="subtle" style={{ padding: 18, margin: 0 }}>
                No hay partidos pendientes. Para simular uno, reprograma un partido a una fecha pasada en la pestaña Partidos.
              </p>
            ) : (
              pending.map((m) => <ResultRow key={m.id} match={m} onDone={() => reload(true)} />)
            )}
          </div>
          <h2 className="section-title">
            Finalizados <span className="count">{finished.length}</span>
          </h2>
          <div className="card">
            {finished.length === 0 ? (
              <p className="subtle" style={{ padding: 18, margin: 0 }}>
                Aún no hay resultados.
              </p>
            ) : (
              finished.map((m) => <ResultRow key={m.id} match={m} onDone={() => reload(true)} />)
            )}
          </div>
        </>
      ) : (
        <>
          <h2 className="section-title" style={{ marginTop: 0 }}>Programar partido</h2>
          <CreateMatch onDone={() => reload(true)} />
          <h2 className="section-title">
            Próximos <span className="count">{upcoming.length}</span>
          </h2>
          <div className="card">
            {upcoming.map((m) => (
              <ScheduleRow key={m.id} match={m} onDone={() => reload(true)} />
            ))}
          </div>
        </>
      )}
    </>
  );
}
