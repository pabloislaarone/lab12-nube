import { useCallback, useEffect, useRef, useState } from 'react';
import { api, onInstance } from './api.js';

/** Carga datos de la API. reload(true) recarga sin mostrar el estado de carga. */
export function useFetch(path) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const current = useRef(path);
  current.current = path;

  const reload = useCallback(
    async (silent = false) => {
      if (!path) return;
      if (!silent) setState((s) => ({ ...s, loading: true, error: null }));
      try {
        const data = await api(path);
        if (current.current === path) setState({ data, loading: false, error: null });
      } catch (error) {
        if (current.current === path) setState((s) => ({ ...s, loading: false, error }));
      }
    },
    [path],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  const setData = useCallback((updater) => {
    setState((s) => ({ ...s, data: typeof updater === 'function' ? updater(s.data) : updater }));
  }, []);

  return { ...state, reload, setData };
}

/** Ejecuta el callback cada vez que el worker avisa que el ranking cambió (vía SSE). */
export function useRankingUpdates(callback) {
  const ref = useRef(callback);
  ref.current = callback;
  useEffect(() => {
    const handler = (event) => ref.current(event.detail);
    window.addEventListener('polla:ranking', handler);
    return () => window.removeEventListener('polla:ranking', handler);
  }, []);
}

/** Hora actual que se actualiza cada `intervalMs`. */
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

/** Réplica de la API que atendió la última petición. */
export function useInstance() {
  const [instance, setInstance] = useState(null);
  useEffect(() => onInstance(setInstance), []);
  return instance;
}
