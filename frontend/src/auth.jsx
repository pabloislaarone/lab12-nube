import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, onUnauthorized, tokenStore } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    onUnauthorized(logout);
    if (!tokenStore.get()) {
      setReady(true);
      return;
    }
    api('/auth/me')
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setReady(true));
  }, [logout]);

  const handleAuth = useCallback((response) => {
    tokenStore.set(response.token);
    setUser(response.user);
    return response.user;
  }, []);

  const login = useCallback(
    (email, password) => api('/auth/login', { method: 'POST', body: { email, password } }).then(handleAuth),
    [handleAuth],
  );

  const register = useCallback(
    (name, email, password) =>
      api('/auth/register', { method: 'POST', body: { name, email, password } }).then(handleAuth),
    [handleAuth],
  );

  const value = useMemo(
    () => ({ user, ready, login, register, logout, isAdmin: user?.role === 'ADMIN' }),
    [user, ready, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
