import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { PlusIcon, UsersIcon } from '../components/Icons.jsx';
import { Empty, ErrorBox, Modal, Skeletons } from '../components/ui.jsx';
import { useFetch } from '../hooks.js';

function CreateRoomModal({ open, onClose }) {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const room = await api('/rooms', { method: 'POST', body: { name, description } });
      toast.success('Sala creada', { description: `Comparte el código ${room.inviteCode} para invitar.` });
      onClose();
      navigate(`/salas/${room.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose}>
      <h2>Nueva sala</h2>
      <p className="muted" style={{ margin: '0 0 18px' }}>
        Se generará un código de invitación para que tus amigos se unan.
      </p>
      <form onSubmit={submit}>
        {error && <div className="form-error">{error}</div>}
        <div className="field">
          <label htmlFor="room-name">Nombre</label>
          <input id="room-name" className="input" value={name} onChange={(e) => setName(e.target.value)} required minLength={3} maxLength={60} placeholder="Ej. Salón 5B TECSUP" autoFocus />
        </div>
        <div className="field">
          <label htmlFor="room-desc">Descripción (opcional)</label>
          <textarea id="room-desc" className="input" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={200} />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancelar
          </button>
          <button className="btn btn-primary" disabled={busy}>
            <span className="btn-label" data-busy={busy}>Crear sala</span>
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function Rooms() {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const { data, loading, error, reload } = useFetch('/rooms');
  const [creating, setCreating] = useState(false);
  const [code, setCode] = useState('');

  function join(e) {
    e.preventDefault();
    if (code.trim()) navigate(`/unirse/${code.trim().toUpperCase()}`);
  }

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">Grupos privados</div>
          <h1>Salas</h1>
          <p>Compite con tu grupo: cada sala tiene su propio ranking y se entra solo con invitación.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setCreating(true)}>
          <PlusIcon /> Crear sala
        </button>
      </div>

      <form className="card card-pad row" onSubmit={join} style={{ marginBottom: 22, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 220px' }}>
          <strong>¿Te invitaron?</strong>
          <div className="subtle" style={{ fontSize: 13 }}>Ingresa el código de 8 caracteres.</div>
        </div>
        <input
          className="input input-code"
          style={{ flex: '1 1 180px', maxWidth: 240 }}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="ABCD2345"
          maxLength={12}
          aria-label="Código de invitación"
        />
        <button className="btn" disabled={!code.trim()}>
          Unirme
        </button>
      </form>

      {loading && !data ? (
        <Skeletons count={3} height={150} className="room-grid" />
      ) : error ? (
        <ErrorBox error={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <Empty
          title="Todavía no estás en ninguna sala"
          action={
            <button className="btn btn-primary" onClick={() => setCreating(true)}>
              Crear mi primera sala
            </button>
          }
        >
          {isAdmin ? 'El administrador puede crear salas para probar el flujo.' : 'Crea una y comparte el código, o únete con el de un amigo.'}
        </Empty>
      ) : (
        <div className="room-grid stagger">
          {data.map((room) => (
            <Link key={room.id} to={`/salas/${room.id}`} className="card card-link room-card">
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <span className={`chip ${room.myRole === 'OWNER' ? 'chip-accent' : ''}`}>
                  {room.myRole === 'OWNER' ? 'Dueño' : 'Miembro'}
                </span>
                <span className="subtle" style={{ fontSize: 13, display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                  <UsersIcon width={15} height={15} /> {room.membersCount}
                </span>
              </div>
              <div>
                <h3>{room.name}</h3>
                <p className="muted" style={{ margin: '4px 0 0', fontSize: 13.5 }}>
                  {room.description || `Creada por ${room.ownerName}`}
                </p>
              </div>
              <span className="subtle" style={{ fontSize: 12.5, marginTop: 'auto' }}>
                Código <span className="input-code">{room.inviteCode}</span>
              </span>
            </Link>
          ))}
        </div>
      )}

      <CreateRoomModal open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
