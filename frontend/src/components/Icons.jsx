// Íconos SVG en línea (trazo de 1.8 px, heredan el color del texto)
const base = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
};

export const BallIcon = (p) => (
  <svg {...base} {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7.5l4 2.9-1.5 4.6h-5L8 10.4z" />
    <path d="M12 3v4.5M16 10.4l4.3-1.6M14.5 15l2.7 3.8M9.5 15l-2.7 3.8M8 10.4L3.7 8.8" />
  </svg>
);

export const UsersIcon = (p) => (
  <svg {...base} {...p}>
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20c.7-3.4 3.3-5.5 6.5-5.5s5.8 2.1 6.5 5.5" />
    <path d="M16 4.6a3.5 3.5 0 010 6.8M18 14.8c1.9.8 3.1 2.6 3.5 5.2" />
  </svg>
);

export const TrophyIcon = (p) => (
  <svg {...base} {...p}>
    <path d="M7 4h10v5a5 5 0 01-10 0z" />
    <path d="M7 6H4a3 3 0 003 4M17 6h3a3 3 0 01-3 4M12 14v4M8 21h8M9 18h6" />
  </svg>
);

export const ChartIcon = (p) => (
  <svg {...base} {...p}>
    <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />
  </svg>
);

export const BookIcon = (p) => (
  <svg {...base} {...p}>
    <path d="M4 5a2 2 0 012-2h13v16H6a2 2 0 00-2 2z" />
    <path d="M4 21V5M9 7h6" />
  </svg>
);

export const ShieldIcon = (p) => (
  <svg {...base} {...p}>
    <path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z" />
  </svg>
);

export const FlameIcon = (p) => (
  <svg {...base} width={14} height={14} {...p}>
    <path d="M12 3c1 3.5 5 5.5 5 10a5 5 0 01-10 0c0-2.2 1-3.6 2-4.6.3 1.6 1.2 2.6 2 3 0-3.3.2-5.8 1-8.4z" />
  </svg>
);

export const ClockIcon = (p) => (
  <svg {...base} width={14} height={14} {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);

export const CheckIcon = (p) => (
  <svg {...base} width={16} height={16} {...p}>
    <path d="M5 12.5l4.5 4.5L19 7.5" />
  </svg>
);

export const CopyIcon = (p) => (
  <svg {...base} width={16} height={16} {...p}>
    <rect x="8" y="8" width="12" height="12" rx="2" />
    <path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2" />
  </svg>
);

export const PlusIcon = (p) => (
  <svg {...base} width={16} height={16} {...p}>
    <path d="M12 5v14M5 12h14" />
  </svg>
);

export const LogoutIcon = (p) => (
  <svg {...base} width={16} height={16} {...p}>
    <path d="M15 4h3a2 2 0 012 2v12a2 2 0 01-2 2h-3M10 17l5-5-5-5M15 12H4" />
  </svg>
);

export const BoltIcon = (p) => (
  <svg {...base} width={14} height={14} {...p}>
    <path d="M13 3L5 14h6l-1 7 8-11h-6z" />
  </svg>
);
