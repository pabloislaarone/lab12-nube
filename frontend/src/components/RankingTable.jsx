import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth.jsx';
import { FlameIcon } from './Icons.jsx';
import { Avatar } from './ui.jsx';

/** Tabla de posiciones. Las filas cuyo puntaje cambió destellan una vez. */
export default function RankingTable({ rows }) {
  const { user } = useAuth();
  const previous = useRef(new Map());
  const [flashing, setFlashing] = useState(new Set());

  useEffect(() => {
    const changed = new Set();
    rows.forEach((r) => {
      const before = previous.current.get(r.userId);
      if (before !== undefined && before !== r.totalPoints) changed.add(r.userId);
    });
    previous.current = new Map(rows.map((r) => [r.userId, r.totalPoints]));
    if (changed.size === 0) return undefined;
    setFlashing(changed);
    const id = setTimeout(() => setFlashing(new Set()), 1300);
    return () => clearTimeout(id);
  }, [rows]);

  return (
    <div className="card table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th style={{ width: 52 }}>#</th>
            <th>Participante</th>
            <th className="right">Puntos</th>
            <th className="right hide-sm">Exactos</th>
            <th className="right hide-sm">Aciertos</th>
            <th className="right">Racha</th>
            <th className="right hide-sm">Pronósticos</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const classes = [r.userId === user?.id ? 'me' : '', flashing.has(r.userId) ? 'flash' : ''].join(' ');
            return (
              <tr key={r.userId} className={classes}>
                <td>
                  <span className={`pos pos-${r.position}`}>{r.position}</span>
                </td>
                <td>
                  <span className="person">
                    <Avatar name={r.name} />
                    <span>
                      {r.name}
                      {r.userId === user?.id && <span className="subtle"> · tú</span>}
                    </span>
                  </span>
                </td>
                <td className="right">
                  <strong className="num" style={{ fontSize: 17 }}>
                    {r.totalPoints}
                  </strong>
                </td>
                <td className="right num hide-sm">{r.exactHits}</td>
                <td className="right num hide-sm">{r.winnerHits}</td>
                <td className="right">
                  {r.currentStreak > 0 ? (
                    <span className="streak num" title={`Mejor racha: ${r.bestStreak}`}>
                      <FlameIcon /> {r.currentStreak}
                    </span>
                  ) : (
                    <span className="subtle num">0</span>
                  )}
                </td>
                <td className="right num hide-sm subtle">{r.predictedCount}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
