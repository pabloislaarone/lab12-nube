import { useMemo, useState } from 'react';
import { useAuth } from '../auth.jsx';
import MatchCard from '../components/MatchCard.jsx';
import { Empty, ErrorBox, Skeletons } from '../components/ui.jsx';
import { useFetch, useNow, useRankingUpdates } from '../hooks.js';

const FILTERS = [
  { id: 'open', label: 'Por jugar' },
  { id: 'finished', label: 'Finalizados' },
  { id: 'all', label: 'Todos' },
];

export default function Matches() {
  const { isAdmin } = useAuth();
  const { data, loading, error, reload, setData } = useFetch('/matches');
  const [filter, setFilter] = useState('open');
  const now = useNow(30_000);

  // Cuando el worker recalcula, se recargan los puntos sin parpadeo
  useRankingUpdates(() => reload(true));

  const matches = useMemo(() => {
    const list = data ?? [];
    if (filter === 'open') return list.filter((m) => m.status !== 'FINISHED');
    if (filter === 'finished') return [...list.filter((m) => m.status === 'FINISHED')].reverse();
    return list;
  }, [data, filter]);

  const predicted = (data ?? []).filter((m) => m.myPrediction && m.status !== 'FINISHED').length;
  const open = (data ?? []).filter((m) => !m.locked).length;

  function handleSaved(updated) {
    setData((list) => list.map((m) => (m.id === updated.id ? updated : m)));
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Fixture</div>
          <h1>Partidos</h1>
          <p>
            Puedes editar tu pronóstico hasta el pitazo inicial.{' '}
            {!isAdmin && data && (
              <span className="subtle">
                Llevas {predicted} pronósticos · {open} partidos abiertos.
              </span>
            )}
          </p>
        </div>
        <div className="tabs" role="tablist" aria-label="Filtrar partidos">
          {FILTERS.map((f) => (
            <button key={f.id} role="tab" aria-selected={filter === f.id} onClick={() => setFilter(f.id)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {loading && !data ? (
        <Skeletons />
      ) : error ? (
        <ErrorBox error={error} onRetry={reload} />
      ) : matches.length === 0 ? (
        <Empty title="No hay partidos aquí">
          {filter === 'finished' ? 'Aún no se registran resultados.' : 'No hay partidos pendientes.'}
        </Empty>
      ) : (
        <div className="match-grid stagger" key={filter}>
          {matches.map((m) => (
            <MatchCard key={m.id} match={m} now={now} canPredict={!isAdmin} onSaved={handleSaved} />
          ))}
        </div>
      )}
    </>
  );
}
