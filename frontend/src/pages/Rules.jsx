import { ErrorBox, Skeletons } from '../components/ui.jsx';
import { useFetch } from '../hooks.js';

export default function Rules() {
  const { data: r, loading, error, reload } = useFetch('/rules');

  if (loading && !r) return <Skeletons count={5} height={110} className="rules" />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const highest = r.combineMode === 'HIGHEST';
  const rules = [
    {
      points: r.exactPoints,
      unit: 'puntos',
      title: '1 · Resultado exacto',
      text: 'Aciertas el marcador exacto del partido.',
      examples: ['Pronóstico 2–1 · Final 2–1 → +' + r.exactPoints],
    },
    {
      points: r.winnerPoints,
      unit: 'puntos',
      title: '2 · Ganador correcto',
      text: 'Aciertas qué equipo gana, o que empatan, aunque no el marcador.',
      examples: ['Pronóstico 1–0 · Final 3–0 → +' + r.winnerPoints, 'Pronóstico 1–1 · Final 2–2 → +' + r.winnerPoints],
    },
    {
      points: r.goalDiffPoints,
      unit: 'puntos',
      title: '3 · Diferencia de goles correcta',
      text: highest
        ? 'Aciertas el margen de victoria. Como en esta polla vale solo la regla más alta, este acierto queda cubierto por el de ganador (3 puntos).'
        : 'Aciertas el margen de victoria. En el modo acumulativo se suma al punto por ganador.',
      examples: [
        `Pronóstico 3–1 · Final 2–0 → ${highest ? '+' + r.winnerPoints + ' (vale la más alta)' : '+' + (r.winnerPoints + r.goalDiffPoints) + ' (3 + 2)'}`,
      ],
    },
    {
      points: `+${r.streakBonus}`,
      unit: 'bonus',
      title: `4 · Racha de ${r.streakLength}`,
      text: `Por cada ${r.streakLength} partidos seguidos en los que aciertas al menos el ganador, sumas ${r.streakBonus} puntos extra. Si fallas o no pronosticas un partido, la racha vuelve a cero.`,
      examples: [`Aciertos 1, 2 y 3 → +${r.streakBonus}`, `Aciertos 4, 5 y 6 → +${r.streakBonus} otra vez`],
    },
    {
      points: `+${r.earlyBonus}`,
      unit: 'bonus',
      title: '5 · Predicción anticipada',
      text: `Si registras tu pronóstico con más de ${r.earlyThresholdHours} horas de anticipación, ganas ${r.earlyBonus} punto extra${r.earlyBonusRequiresHit ? ' (si sumaste puntos en ese partido)' : ''}. Lo registrado en los últimos ${r.lastMinuteWindowMinutes} minutos solo recibe los puntos base y no cuenta para la racha.`,
      examples: ['Cuenta la hora de tu último cambio', 'Los pronósticos se cierran al inicio del partido'],
    },
  ];

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Sistema de puntuación</div>
          <h1>Reglas</h1>
          <p>
            Los valores vienen del servidor, así que esta página siempre muestra la configuración activa. Modo de combinación:{' '}
            <strong>{highest ? 'solo vale la regla más alta' : 'acumulativo'}</strong>.
          </p>
        </div>
      </div>
      <div className="rules stagger">
        {rules.map((rule) => (
          <article key={rule.title} className="card rule">
            <div className="rule-points">
              {rule.points}
              <small>{rule.unit}</small>
            </div>
            <div>
              <h3>{rule.title}</h3>
              <p>{rule.text}</p>
              <div className="example">
                {rule.examples.map((e) => (
                  <span key={e} className="chip">
                    {e}
                  </span>
                ))}
              </div>
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
