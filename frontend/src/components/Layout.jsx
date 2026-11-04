import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '../auth.jsx';
import { useInstance } from '../hooks.js';
import { initials } from '../format.js';
import { BallIcon, BookIcon, ChartIcon, LogoutIcon, ShieldIcon, TrophyIcon, UsersIcon } from './Icons.jsx';

const LINKS = [
  { to: '/partidos', label: 'Partidos', icon: BallIcon },
  { to: '/salas', label: 'Salas', icon: UsersIcon },
  { to: '/ranking', label: 'Ranking', icon: TrophyIcon },
  { to: '/estadisticas', label: 'Estadísticas', icon: ChartIcon },
  { to: '/reglas', label: 'Reglas', icon: BookIcon },
];

/** Escucha los avisos en tiempo real del servidor (SSE) y los reenvía a las páginas. */
function useServerEvents() {
  useEffect(() => {
    let source;
    try {
      source = new EventSource('/api/stream');
    } catch {
      return undefined;
    }
    source.addEventListener('ranking', (event) => {
      let detail = {};
      try {
        detail = JSON.parse(event.data);
      } catch {
        /* evento sin datos */
      }
      window.dispatchEvent(new CustomEvent('marcador:ranking', { detail }));
      toast('Puntajes actualizados', { description: 'El ranking se recalculó con los últimos resultados.' });
    });
    return () => source.close();
  }, []);
}

/** Muestra qué réplica de la API respondió la última petición (evidencia del balanceo). */
function ReplicaIndicator() {
  const instance = useInstance();
  const [changed, setChanged] = useState(false);
  const previous = useRef(instance);

  useEffect(() => {
    if (previous.current && instance && previous.current !== instance) {
      setChanged(true);
      const id = setTimeout(() => setChanged(false), 180);
      previous.current = instance;
      return () => clearTimeout(id);
    }
    previous.current = instance;
    return undefined;
  }, [instance]);

  return (
    <span className="replica" title="Réplica de la API que atendió la última petición">
      <span className="replica-dot" />
      réplica <span className="replica-id" data-changed={changed}>{instance ?? '—'}</span>
    </span>
  );
}

export default function Layout() {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  useServerEvents();

  const links = isAdmin ? [...LINKS, { to: '/admin', label: 'Admin', icon: ShieldIcon }] : LINKS;

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/partidos" className="brand">
            <span className="brand-mark">
              <BallIcon width={17} height={17} />
            </span>
            Marcador 26
          </NavLink>
          <nav className="nav" aria-label="Principal">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to}>
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="user-chip">
            <span className="avatar">{initials(user?.name)}</span>
            <span className="user-name">{user?.name}</span>
            <button className="btn btn-ghost btn-sm icon-btn" onClick={handleLogout} aria-label="Cerrar sesión" title="Cerrar sesión">
              <LogoutIcon />
            </button>
          </div>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <footer className="footer">
        <div className="footer-inner">
          <span>Marcador 26 · Mundial 2026 · Ejercicio educativo, sin dinero real</span>
          <ReplicaIndicator />
        </div>
      </footer>

      <nav className="tabbar" aria-label="Navegación inferior">
        {links.map((l) => (
          <NavLink key={l.to} to={l.to}>
            <l.icon />
            {l.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
