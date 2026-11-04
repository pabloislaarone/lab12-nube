import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '../api.js';
import { UsersIcon } from '../components/Icons.jsx';
import { useFetch } from '../hooks.js';

export default function JoinRoom() {
  const { code } = useParams();
  const navigate = useNavigate();
  const { data, loading, error } = useFetch(`/rooms/invite/${encodeURIComponent(code)}`);
  const [busy, setBusy] = useState(false);

  async function join() {
    setBusy(true);
    try {
      const room = await api('/rooms/join', { method: 'POST', body: { code } });
      toast.success(`Te uniste a ${room.name}`);
      navigate(`/salas/${room.id}`, { replace: true });
    } catch (err) {
      toast.error(err.message);
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 440, margin: '6vh auto 0' }}>
      <div className="card card-pad" style={{ padding: 28 }}>
        <div className="eyebrow">Invitación</div>
        {loading ? (
          <div className="skeleton" style={{ height: 120 }} />
        ) : error ? (
          <>
            <h1 style={{ fontSize: 26, marginBottom: 8 }}>Código no válido</h1>
            <p className="muted">
              El código <span className="input-code">{code}</span> no existe o fue reemplazado por uno nuevo. Pide a tu amigo el
              enlace actualizado.
            </p>
            <Link to="/salas" className="btn" style={{ marginTop: 10 }}>
              Ir a mis salas
            </Link>
          </>
        ) : (
          <>
            <h1 style={{ fontSize: 28, marginBottom: 6 }}>{data.name}</h1>
            <p className="muted" style={{ margin: '0 0 18px' }}>
              {data.description || `Sala creada por ${data.ownerName}`}
            </p>
            <p className="subtle" style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '0 0 22px' }}>
              <UsersIcon width={16} height={16} /> {data.membersCount} participantes · dueño {data.ownerName}
            </p>
            {data.alreadyMember ? (
              <Link to={`/salas/${data.id}`} className="btn btn-primary btn-block">
                Ya eres miembro · ver sala
              </Link>
            ) : (
              <button className="btn btn-primary btn-block" style={{ height: 42 }} onClick={join} disabled={busy}>
                <span className="btn-label" data-busy={busy}>Unirme a la sala</span>
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}
