import { useEffect, useRef } from 'react';
import { initials, teamColor } from '../format.js';

export function TeamBadge({ code }) {
  return (
    <span className="team-badge" style={{ background: teamColor(code) }} aria-hidden="true">
      {code}
    </span>
  );
}

export function Avatar({ name }) {
  return <span className="avatar">{initials(name)}</span>;
}

export function Empty({ title, children, action }) {
  return (
    <div className="card empty">
      <h3>{title}</h3>
      <p style={{ margin: '0 0 16px' }}>{children}</p>
      {action}
    </div>
  );
}

export function Skeletons({ count = 6, height = 220, className = 'match-grid' }) {
  return (
    <div className={className}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height }} />
      ))}
    </div>
  );
}

export function ErrorBox({ error, onRetry }) {
  return (
    <div className="card empty">
      <h3>No se pudo cargar</h3>
      <p style={{ margin: '0 0 16px' }}>{error?.message}</p>
      {onRetry && (
        <button className="btn" onClick={() => onRetry()}>
          Reintentar
        </button>
      )}
    </div>
  );
}

/** Modal con <dialog> nativo: Esc y foco accesibles sin librerías. */
export function Modal({ open, onClose, children }) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="modal"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
    >
      {open && <div className="modal-body">{children}</div>}
    </dialog>
  );
}

export function ConfirmModal({ open, title, message, confirmLabel = 'Confirmar', danger, busy, onConfirm, onClose }) {
  return (
    <Modal open={open} onClose={onClose}>
      <h2>{title}</h2>
      <p className="muted" style={{ margin: 0 }}>
        {message}
      </p>
      <div className="modal-actions">
        <button className="btn btn-ghost" onClick={onClose}>
          Cancelar
        </button>
        <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm} disabled={busy}>
          {busy && <span className="spinner" />}
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
