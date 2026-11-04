import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { BallIcon } from '../components/Icons.jsx';

export default function Login() {
  const { user, login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const next = new URLSearchParams(location.search).get('next') || '/partidos';

  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={next} replace />;

  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'login') await login(form.email, form.password);
      else await register(form.name, form.email, form.password);
      navigate(next, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <section className="auth-hero">
        <div className="brand">
          <span className="brand-mark">
            <BallIcon width={17} height={17} />
          </span>
          Marcador 26
        </div>
        <div className="pitch" aria-hidden="true" />
        <div>
          <div className="eyebrow">Mundial 2026 · Ejercicio educativo</div>
          <h1>
            Pronostica cada partido. <em>Suma puntos.</em> Gana tu sala.
          </h1>
        </div>
        <div className="mini-rules">
          <div><strong>5</strong>exacto</div>
          <div><strong>3</strong>ganador</div>
          <div><strong>2</strong>diferencia</div>
          <div><strong>+2</strong>racha de 3</div>
          <div><strong>+1</strong>anticipado</div>
        </div>
      </section>

      <section className="auth-form">
        <div className="auth-card">
          <div className="tabs" role="tablist" style={{ marginBottom: 22 }}>
            <button role="tab" aria-selected={mode === 'login'} onClick={() => setMode('login')}>
              Ingresar
            </button>
            <button role="tab" aria-selected={mode === 'register'} onClick={() => setMode('register')}>
              Crear cuenta
            </button>
          </div>

          <h2 style={{ fontSize: 24, marginBottom: 6 }}>{mode === 'login' ? 'Bienvenido de vuelta' : 'Únete a Marcador 26'}</h2>
          <p className="muted" style={{ margin: '0 0 22px' }}>
            {mode === 'login' ? 'Ingresa para ver tus pronósticos y tus salas.' : 'Crea tu cuenta en segundos, sin dinero real.'}
          </p>

          <form onSubmit={submit}>
            {error && <div className="form-error">{error}</div>}
            {mode === 'register' && (
              <div className="field">
                <label htmlFor="name">Nombre</label>
                <input id="name" className="input" value={form.name} onChange={update('name')} required minLength={2} maxLength={80} autoComplete="name" />
              </div>
            )}
            <div className="field">
              <label htmlFor="email">Correo</label>
              <input id="email" type="email" className="input" value={form.email} onChange={update('email')} required autoComplete="email" />
            </div>
            <div className="field">
              <label htmlFor="password">Contraseña</label>
              <input
                id="password"
                type="password"
                className="input"
                value={form.password}
                onChange={update('password')}
                required
                minLength={mode === 'register' ? 6 : 1}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
            </div>
            <button className="btn btn-primary btn-block" style={{ height: 42 }} disabled={busy}>
              <span className="btn-label" data-busy={busy}>
                {mode === 'login' ? 'Ingresar' : 'Crear cuenta'}
              </span>
            </button>
          </form>

          <details className="demo">
            <summary>Cuentas de prueba</summary>
            <p>
              Administrador: <code>admin@marcador.pe</code> / <code>admin123</code>
              <br />
              Participantes demo (después de generarlos en Admin): <code>demo01@marcador.pe</code> / <code>demo123</code>
            </p>
          </details>
        </div>
      </section>
    </div>
  );
}
