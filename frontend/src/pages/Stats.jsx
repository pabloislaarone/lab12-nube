import { useAuth } from '../auth.jsx';
import { Empty, ErrorBox, Skeletons } from '../components/ui.jsx';
import { RULE_LABELS } from '../format.js';
import { useFetch, useRankingUpdates } from '../hooks.js';

const RULE_COLORS = {
  EXACT: 'var(--accent)',
  WINNER: 'var(--info)',
  GOAL_DIFF: 'var(--info)',
  MISS: 'var(--text-3)',
  NOT_PREDICTED: 'var(--danger)',
};

function Kpi({ label, value, suffix }) {
  return (
    <div className="card kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">
        {value}
        {suffix && <small> {suffix}</small>}
      </div>
    </div>
  );
}

/** Puntos acumulados partido a partido (SVG simple, sin librerías). */
function CumulativeChart({ points }) {
  if (points.length === 0) return <p className="subtle">Aún no hay partidos finalizados.</p>;
  const w = 600;
  const h = 220;
  const pad = { l: 34, r: 12, t: 12, b: 28 };
  const max = Math.max(5, ...points.map((p) => p.cumulative));
  const x = (i) => pad.l + (points.length === 1 ? (w - pad.l - pad.r) / 2 : (i * (w - pad.l - pad.r)) / (points.length - 1));
  const y = (v) => h - pad.b - (v / max) * (h - pad.t - pad.b);
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.cumulative).toFixed(1)}`).join(' ');
  const area = `${path} L${x(points.length - 1).toFixed(1)},${y(0)} L${x(0).toFixed(1)},${y(0)} Z`;
  const ticks = [0, Math.round(max / 2), max];

  return (
    <svg className="chart-svg" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Puntos acumulados por partido">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={pad.l} x2={w - pad.r} y1={y(t)} y2={y(t)} stroke="var(--line)" />
          <text x={pad.l - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--text-3)">
            {t}
          </text>
        </g>
      ))}
      <path d={area} fill="var(--accent-soft)" />
      <path d={path} fill="none" stroke="var(--accent)" strokeWidth="2.5" strokeLinejoin="round" />
      {points.map((p, i) => (
        <g key={p.matchId}>
          <circle cx={x(i)} cy={y(p.cumulative)} r="3.5" fill="var(--surface)" stroke="var(--accent)" strokeWidth="2">
            <title>{`${p.label}: +${p.points} (total ${p.cumulative})`}</title>
          </circle>
          {(points.length <= 10 || i % 2 === 0) && (
            <text x={x(i)} y={h - 8} textAnchor="middle" fontSize="10" fill="var(--text-3)">
              {p.label}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
}

export default function Stats() {
  const { isAdmin } = useAuth();
  const { data, loading, error, reload } = useFetch(isAdmin ? null : '/stats/me');
  useRankingUpdates(() => reload(true));

  if (isAdmin) {
    return <Empty title="Solo para participantes">El administrador no pronostica, así que no tiene estadísticas personales.</Empty>;
  }
  if (loading && !data) return <Skeletons count={4} height={100} className="kpis" />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const totalRules = Object.values(data.ruleCounts).reduce((a, b) => a + b, 0) || 1;

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Tu rendimiento</div>
          <h1>Estadísticas</h1>
          <p>
            {data.finishedMatches} partidos finalizados · pronosticaste {data.predictedCount}.
          </p>
        </div>
      </div>

      <div className="kpis stagger">
        <Kpi label="Puntos" value={data.totalPoints} />
        <Kpi label="Posición" value={`#${data.position}`} suffix={`de ${data.participants}`} />
        <Kpi label="Acierto de ganador" value={data.accuracy} suffix="%" />
        <Kpi label="Mejor racha" value={data.bestStreak} suffix={`actual ${data.currentStreak}`} />
      </div>

      <div className="two-col">
        <section className="card card-pad">
          <h3 style={{ fontSize: 16, marginBottom: 16 }}>Cómo sumaste</h3>
          <div className="bars">
            {Object.entries(data.ruleCounts).map(([rule, count]) => (
              <div className="bar-row" key={rule}>
                <span className="muted">{RULE_LABELS[rule]}</span>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width: `${(count / totalRules) * 100}%`, background: RULE_COLORS[rule] }} />
                </div>
                <span className="num right" style={{ textAlign: 'right' }}>
                  {count}
                </span>
              </div>
            ))}
          </div>
          <div className="row" style={{ marginTop: 20, gap: 8, flexWrap: 'wrap' }}>
            <span className="chip chip-accent">Bonus anticipación +{data.earlyPoints}</span>
            <span className="chip chip-warn">Bonus racha +{data.streakPoints}</span>
          </div>
        </section>

        <section className="card card-pad">
          <h3 style={{ fontSize: 16, marginBottom: 8 }}>Puntos acumulados</h3>
          <CumulativeChart points={data.timeline} />
        </section>
      </div>
    </>
  );
}
