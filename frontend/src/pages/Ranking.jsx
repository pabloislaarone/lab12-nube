import RankingTable from '../components/RankingTable.jsx';
import { Empty, ErrorBox, Skeletons } from '../components/ui.jsx';
import { useFetch, useRankingUpdates } from '../hooks.js';

export default function Ranking() {
  const { data, loading, error, reload } = useFetch('/ranking');
  useRankingUpdates(() => reload(true));

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Tabla general</div>
          <h1>Ranking global</h1>
          <p>Todos los participantes. Se actualiza solo cuando se registra un resultado.</p>
        </div>
        <span className="chip chip-accent">
          <span className="replica-dot" /> En vivo
        </span>
      </div>
      {loading && !data ? (
        <Skeletons count={1} height={360} className="" />
      ) : error ? (
        <ErrorBox error={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <Empty title="Aún no hay participantes">Cuando alguien se registre aparecerá aquí.</Empty>
      ) : (
        <RankingTable rows={data} />
      )}
    </>
  );
}
