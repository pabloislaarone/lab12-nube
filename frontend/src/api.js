// Cliente HTTP de la API. Guarda el JWT y avisa qué réplica respondió cada petición.

const TOKEN_KEY = 'marcador.token';

export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* almacenamiento no disponible: la sesión dura lo que la pestaña */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* nada que limpiar */
    }
  },
};

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const instanceListeners = new Set();
let lastInstance = null;
let unauthorizedHandler = null;

/** Se suscribe al id de la réplica que atendió la última petición (cabecera X-Instance-Id). */
export function onInstance(listener) {
  instanceListeners.add(listener);
  if (lastInstance) listener(lastInstance);
  return () => instanceListeners.delete(listener);
}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor');
  }

  const instance = res.headers.get('X-Instance-Id');
  if (instance) {
    lastInstance = instance;
    instanceListeners.forEach((fn) => fn(instance));
  }

  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);

  if (!res.ok) {
    if (res.status === 401 && token && unauthorizedHandler) unauthorizedHandler();
    throw new ApiError(res.status, data?.message || 'Ocurrió un error inesperado');
  }
  return data;
}
