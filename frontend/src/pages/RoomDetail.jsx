import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { CopyIcon } from '../components/Icons.jsx';
import RankingTable from '../components/RankingTable.jsx';
import { Avatar, ConfirmModal, ErrorBox, Modal, Skeletons } from '../components/ui.jsx';
import { formatShort } from '../format.js';
import { useFetch, useRankingUpdates } from '../hooks.js';

async function copy(text, label) {
  try {
    await navigator.clipboard.writeText(text);
    toast.success(`${label} copiado`);
  } catch {
    toast(text, { description: 'Cópialo manualmente' });
  }
}

function EditRoomModal({ room, open, onClose, onSaved }) {
  const [name, setName] = useState(room.name);
  const [description, setDescription] = useState(room.description ?? '');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await api(`/rooms/${room.id}`, { method: 'PATCH', body: { name, description } });
      toast.success('Sala actualizada');
      onSaved();
      onClose();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose}>
      <h2>Editar sala</h2>
      <form onSubmit={submit} style={{ marginTop: 14 }}>
        <div className="field">
          <label htmlFor="edit-name">Nombre</label>
          <input id="edit-name" className="input" value={name} onChange={(e) => setName(e.target.value)} required minLength={3} maxLength={60} />
        </div>
        <div className="field">
          <label htmlFor="edit-desc">Descripción</label>
          <textarea id="edit-desc" className="input" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={200} />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancelar
          </button>
          <button className="btn btn-primary" disabled={busy}>
            Guardar
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function RoomDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const detail = useFetch(`/rooms/${id}`);
  const ranking = useFetch(`/rooms/${id}/ranking`);
  const [tab, setTab] = useState('ranking');
  const [confirm, setConfirm] = useState(null);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  useRankingUpdates(() => ranking.reload(true));

  if (detail.loading && !detail.data) return <Skeletons count={2} height={180} className="rooms" />;
  if (detail.error) return <ErrorBox error={detail.error} onRetry={detail.reload} />;

  const { room, members } = detail.data;
  const isOwner = room.myRole === 'OWNER';
  const link = `${window.location.origin}/unirse/${room.inviteCode}`;

  async function runConfirmed() {
    setBusy(true);
    try {
      await confirm.action();
      setConfirm(null);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  const actions = {
    regenerate: () => ({
      title: '¿Generar un código nuevo?',
      message: 'El código y el enlace actuales dejarán de funcionar. Los miembros actuales se quedan.',
      confirmLabel: 'Generar código',
      action: async () => {
        await api(`/rooms/${room.id}/invite-code`, { method: 'POST' });
        toast.success('Código actualizado');
        detail.reload(true);
      },
    }),
    remove: (member) => ({
      title: `¿Expulsar a ${member.name}?`,
      message: 'Dejará de ver la sala. Podrá volver solo con un código válido.',
      confirmLabel: 'Expulsar',
      danger: true,
      action: async () => {
        await api(`/rooms/${room.id}/members/${member.userId}`, { method: 'DELETE' });
        toast.success(`${member.name} ya no está en la sala`);
        detail.reload(true);
        ranking.reload(true);
      },
    }),
    leave: () => ({
      title: '¿Abandonar la sala?',
      message: 'Tus puntos no se pierden, pero dejarás de aparecer en este ranking.',
      confirmLabel: 'Abandonar',
      danger: true,
      action: async () => {
        await api(`/rooms/${room.id}/leave`, { method: 'POST' });
        toast.success('Saliste de la sala');
        navigate('/salas');
      },
    }),
    delete: () => ({
      title: '¿Eliminar la sala?',
      message: 'Se elimina para todos los miembros. Esta acción no se puede deshacer.',
      confirmLabel: 'Eliminar sala',
      danger: true,
      action: async () => {
        await api(`/rooms/${room.id}`, { method: 'DELETE' });
        toast.success('Sala eliminada');
        navigate('/salas');
      },
    }),
  };

  return (
    <>
      <div className="page-head">
        <div>
          <div className="eyebrow">
            <Link to="/salas" style={{ textDecoration: 'none' }}>
              Salas
            </Link>{' '}
            / {isOwner ? 'Eres el dueño' : `Dueño: ${room.ownerName}`}
          </div>
          <h1>{room.name}</h1>
          {room.description && <p>{room.description}</p>}
        </div>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          {isOwner ? (
            <>
              <button className="btn btn-sm" onClick={() => setEditing(true)}>
                Editar
              </button>
              <button className="btn btn-sm btn-danger" onClick={() => setConfirm(actions.delete())}>
                Eliminar sala
              </button>
            </>
          ) : (
            <button className="btn btn-sm btn-danger" onClick={() => setConfirm(actions.leave())}>
              Abandonar sala
            </button>
          )}
        </div>
      </div>

      <div className="invite" style={{ marginBottom: 26 }}>
        <div style={{ flex: '1 1 200px' }}>
          <div className="subtle" style={{ fontSize: 12.5 }}>Código de invitación</div>
          <div className="invite-code">{room.inviteCode}</div>
        </div>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <button className="btn btn-primary btn-sm" onClick={() => copy(link, 'Enlace')}>
            <CopyIcon /> Copiar enlace
          </button>
          <button className="btn btn-sm" onClick={() => copy(room.inviteCode, 'Código')}>
            Copiar código
          </button>
          {isOwner && (
            <button className="btn btn-sm btn-ghost" onClick={() => setConfirm(actions.regenerate())}>
              Nuevo código
            </button>
          )}
        </div>
      </div>

      <div className="tabs" role="tablist" style={{ marginBottom: 16 }}>
        <button role="tab" aria-selected={tab === 'ranking'} onClick={() => setTab('ranking')}>
          Ranking
        </button>
        <button role="tab" aria-selected={tab === 'members'} onClick={() => setTab('members')}>
          Miembros · {members.length}
        </button>
      </div>

      {tab === 'ranking' ? (
        ranking.loading && !ranking.data ? (
          <Skeletons count={1} height={260} className="" />
        ) : ranking.error ? (
          <ErrorBox error={ranking.error} onRetry={ranking.reload} />
        ) : (
          <RankingTable rows={ranking.data} />
        )
      ) : (
        <div className="card card-pad">
          <ul className="member-list">
            {members.map((m) => (
              <li key={m.userId}>
                <span className="person">
                  <Avatar name={m.name} />
                  <span>
                    {m.name}
                    {m.userId === user.id && <span className="subtle"> · tú</span>}
                    <div className="subtle" style={{ fontSize: 12.5 }}>
                      Se unió el {formatShort(m.joinedAt)}
                    </div>
                  </span>
                </span>
                {m.role === 'OWNER' ? (
                  <span className="chip chip-accent">Dueño</span>
                ) : isOwner ? (
                  <button className="btn btn-sm btn-ghost" onClick={() => setConfirm(actions.remove(m))}>
                    Expulsar
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      )}

      <ConfirmModal
        open={Boolean(confirm)}
        title={confirm?.title}
        message={confirm?.message}
        confirmLabel={confirm?.confirmLabel}
        danger={confirm?.danger}
        busy={busy}
        onConfirm={runConfirmed}
        onClose={() => setConfirm(null)}
      />
      {editing && <EditRoomModal room={room} open={editing} onClose={() => setEditing(false)} onSaved={() => detail.reload(true)} />}
    </>
  );
}
