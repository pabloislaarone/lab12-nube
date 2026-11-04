const dateFormat = new Intl.DateTimeFormat('es-PE', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

const shortDate = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatKickoff(iso) {
  return dateFormat.format(new Date(iso));
}

export function formatShort(iso) {
  return shortDate.format(new Date(iso));
}

/** "2 d 4 h", "3 h 12 min", "8 min" */
export function formatDuration(ms) {
  const minutes = Math.max(0, Math.floor(ms / 60_000));
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  if (days > 0) return `${days} d ${hours} h`;
  if (hours > 0) return `${hours} h ${mins} min`;
  return `${mins} min`;
}

export function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('');
}

/** Color estable por código de selección (para el escudo genérico). */
export function teamColor(code = '') {
  let hash = 0;
  for (const ch of code) hash = (hash * 31 + ch.charCodeAt(0)) % 360;
  return `hsl(${hash} 55% 36%)`;
}

export const RULE_LABELS = {
  EXACT: 'Marcador exacto',
  WINNER: 'Ganador correcto',
  GOAL_DIFF: 'Diferencia correcta',
  MISS: 'Sin acierto',
  NOT_PREDICTED: 'No pronosticaste',
};

export const HOUR = 3_600_000;
export const MINUTE = 60_000;
