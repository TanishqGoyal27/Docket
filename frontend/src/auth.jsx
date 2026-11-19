import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(undefined); // undefined = still checking the session

  const refresh = useCallback(() => {
    api('/auth/me').then(setUser).catch(() => setUser(null));
  }, []);

  useEffect(() => {
    refresh();
    const expired = () => setUser(null);
    window.addEventListener('docket:session-expired', expired);
    return () => window.removeEventListener('docket:session-expired', expired);
  }, [refresh]);

  const login = async (email, password) => {
    const me = await api('/auth/login', { method: 'POST', body: { email, password } });
    setUser(me);
    return me;
  };

  const register = async (details) => {
    const me = await api('/auth/register', { method: 'POST', body: details });
    setUser(me);
    return me;
  };

  const logout = async () => {
    await api('/auth/logout', { method: 'POST' }).catch(() => {});
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
