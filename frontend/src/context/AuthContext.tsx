import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokens } from '../lib/api';
import type { AuthResponse, UserDto } from '../lib/types';

interface AuthContextValue {
  user: UserDto | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<UserDto>;
  register: (input: { email: string; password: string; fullName: string; phone?: string }) => Promise<UserDto>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  setUser: (user: UserDto) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!tokens.access() && !tokens.refresh()) {
      setUser(null);
      return;
    }
    try {
      setUser(await api.me());
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    refreshUser().finally(() => setLoading(false));
  }, [refreshUser]);

  const login = useCallback(async (email: string, password: string) => {
    const auth: AuthResponse = await api.login({ email, password });
    tokens.save(auth);
    setUser(auth.user);
    return auth.user;
  }, []);

  const register = useCallback(
    async (input: { email: string; password: string; fullName: string; phone?: string }) => {
      const auth = await api.register(input);
      tokens.save(auth);
      setUser(auth.user);
      return auth.user;
    },
    [],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokens.refresh();
    tokens.clear();
    setUser(null);
    if (refreshToken) {
      // Best-effort server-side revocation; the local session is already gone.
      await api.logout(refreshToken).catch(() => undefined);
    }
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout, refreshUser, setUser }),
    [user, loading, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
