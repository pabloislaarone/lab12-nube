import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { api } from '../api.js';
import { HOUR, MINUTE, RULE_LABELS, formatDuration, formatKickoff } from '../format.js';
import { BoltIcon, CheckIcon, ClockIcon, FlameIcon } from './Icons.jsx';
import { TeamBadge } from './ui.jsx';

function Stepper({ value, onChange, label, disabled }) {
  return (
    <div className="stepper" role="group" aria-label={label}>
      <button type="button" onClick={() => onChange(Math.max(0, value - 1))} disabled={disabled || value <= 0} aria-label={`Restar gol a ${label}`}>
        −
      </button>
      <output aria-live="polite">{value}</output>
      <button type="button" onClick={() => onChange(Math.min(20, value + 1))} disabled={disabled} aria-label={`Sumar gol a ${label}`}>
        +
      </button>
    </div>
  );
}

/** Estado de la regla 5 según cuánto falta para el partido. */
function TimingHint({ msLeft, prediction }) {
  if (prediction?.early) {
    return (
      <span className="chip chip-accent">
        <BoltIcon /> Anticipada: +1 si aciertas
      </span>
    );
  }
  if (prediction?.lastMinute) {
    return <span className="chip chip-warn">Último minuto: solo puntos base</span>;
  }
  if (msLeft > 24 * HOUR) {
    return (
      <span className="chip chip-accent">
        <BoltIcon /> Pronostica ya: +1 anticipado
      </span>
    );
  }
  if (msLeft < 10 * MINUTE) {
    return <span className="chip chip-warn">Último minuto: solo puntos base</span>;
  }
  return null;
}

function Breakdown({ score }) {
  if (!score) return null;
  const ruleChip = {
    EXACT: 'chip-accent',
    WINNER: 'chip-info',
    GOAL_DIFF: 'chip-info',
    MISS: '',
    NOT_PREDICTED: 'chip-danger',
  }[score.rule];
  return (
    <div className="breakdown">
      <span className={`chip ${ruleChip}`}>
        {RULE_LABELS[score.rule]}
        {score.basePoints > 0 && ` +${score.basePoints}`}
      </span>
      {score.earlyBonus > 0 && (
        <span className="chip chip-accent">
          <BoltIcon /> Anticipada +{score.earlyBonus}
        </span>
      )}
      {score.streakBonus > 0 && (
        <span className="chip chip-warn">
          <FlameIcon /> Racha +{score.streakBonus}
        </span>
      )}
    </div>
  );
}

export default function MatchCard({ match, now, canPredict, onSaved }) {
  const [home, setHome] = useState(match.myPrediction?.homeGoals ?? 0);
  const [away, setAway] = useState(match.myPrediction?.awayGoals ?? 0);
  const [saving, setSaving] = useState(false);
  const [justSaved, setJustSaved] = useState(false);

  // Si llega una versión nueva del partido (otro dispositivo, recarga), se sincroniza
  useEffect(() => {
    setHome(match.myPrediction?.homeGoals ?? 0);
    setAway(match.myPrediction?.awayGoals ?? 0);
  }, [match.myPrediction?.homeGoals, match.myPrediction?.awayGoals]);

  useEffect(() => {
    if (!justSaved) return undefined;
    const id = setTimeout(() => setJustSaved(false), 1600);
    return () => clearTimeout(id);
  }, [justSaved]);

  const kickoff = new Date(match.kickoffAt).getTime();
  const msLeft = kickoff - now;
  const finished = match.status === 'FINISHED';
  const locked = match.locked || msLeft <= 0;
  const hasPrediction = Boolean(match.myPrediction);
  const dirty = !hasPrediction || home !== match.myPrediction.homeGoals || away !== match.myPrediction.awayGoals;

  async function save() {
    setSaving(true);
    try {
      const updated = await api(`/matches/${match.id}/prediction`, {
        method: 'PUT',
        body: { homeGoals: home, awayGoals: away },
      });
      setJustSaved(true);
      onSaved?.(updated);
    } catch (error) {
      toast.error(error.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="card match">
      <div className="match-meta">
        <span>{match.stage} · {match.venue}</span>
        {finished ? (
          <span className="chip">Final</span>
        ) : locked ? (
          <span className="chip chip-warn">En juego</span>
        ) : (
          <span className="chip">
            <ClockIcon /> {formatDuration(msLeft)}
          </span>
        )}
      </div>

      <div className="match-teams">
        <div className="team">
          <TeamBadge code={match.homeCode} />
          <span className="team-name">{match.homeTeam}</span>
        </div>
        {finished ? (
          <div className="scoreline" aria-label={`Resultado ${match.homeGoals} a ${match.awayGoals}`}>
            <span>{match.homeGoals}</span>
            <span className="sep">–</span>
            <span>{match.awayGoals}</span>
          </div>
        ) : (
          <span className="vs">VS</span>
        )}
        <div className="team">
          <TeamBadge code={match.awayCode} />
          <span className="team-name">{match.awayTeam}</span>
        </div>
      </div>

      {!locked && canPredict && (
        <div className="predict-row">
          <Stepper value={home} onChange={setHome} label={match.homeTeam} disabled={saving} />
          <span className="subtle">tu pronóstico</span>
          <Stepper value={away} onChange={setAway} label={match.awayTeam} disabled={saving} />
        </div>
      )}

      <div className="match-foot">
        {finished ? (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <span className="hint">
                {hasPrediction
                  ? `Tu pronóstico: ${match.myPrediction.homeGoals}–${match.myPrediction.awayGoals}`
                  : canPredict
                    ? 'No pronosticaste este partido'
                    : formatKickoff(match.kickoffAt)}
              </span>
              <Breakdown score={match.myScore} />
            </div>
            {match.myScore && (
              <span className="points-total">
                +{match.myScore.total}
                <small>pts</small>
              </span>
            )}
          </>
        ) : locked ? (
          <span className="hint">
            {hasPrediction
              ? `Tu pronóstico: ${match.myPrediction.homeGoals}–${match.myPrediction.awayGoals} · esperando resultado`
              : 'Pronósticos cerrados · esperando resultado'}
          </span>
        ) : canPredict ? (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-start' }}>
              <span className="hint">{formatKickoff(match.kickoffAt)}</span>
              <TimingHint msLeft={msLeft} prediction={dirty ? null : match.myPrediction} />
            </div>
            <button className="btn btn-primary btn-sm" onClick={save} disabled={saving || (!dirty && !justSaved)}>
              <span className="btn-label" data-busy={saving}>
                {justSaved ? (
                  <>
                    <CheckIcon /> Guardado
                  </>
                ) : hasPrediction ? (
                  'Actualizar'
                ) : (
                  'Guardar'
                )}
              </span>
            </button>
          </>
        ) : (
          <span className="hint">
            {formatKickoff(match.kickoffAt)} · {match.predictionsCount} pronósticos
          </span>
        )}
      </div>
    </article>
  );
}
