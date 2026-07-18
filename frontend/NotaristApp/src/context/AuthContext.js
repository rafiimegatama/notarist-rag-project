import React, { createContext, useContext, useState, useEffect } from 'react';
import { login, logout, getStoredToken } from '../api/auth';
import { setAuthFailureHandler } from '../api/client';
import { useGlobalLoading } from './LoadingContext';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const globalLoading = useGlobalLoading();

  useEffect(() => {
    (async () => {
      try {
        const token = await getStoredToken();
        if (token) {
          setUser({ token });
        }
      } catch (_) {
        // A failed session restore must never strand the app on the splash screen:
        // any storage failure here means "no session", and the user falls through to
        // the login screen. Without the finally, one rejected promise froze the whole
        // app at "Memuat sesi…" (seen live: SecureStore rejecting on web).
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  useEffect(() => {
    setAuthFailureHandler(() => setUser(null));
    return () => setAuthFailureHandler(null);
  }, []);

  const signIn = async (username, password) => {
    const data = await login(username, password);
    setUser({ token: data.accessToken, ...data });
    return data;
  };

  // Signing out is a genuinely app-wide, blocking action: it clears the session and swaps the whole
  // navigator to the auth stack, so a full-screen overlay is exactly right while it runs. Routed
  // through global loading (Sprint 9) rather than a local spinner because there is no single screen
  // that owns "the app is logging out". withLoading always runs the work, even if the provider is
  // somehow absent, so sign-out can never be silently skipped.
  const signOut = async () => {
    await globalLoading.withLoading(
      async () => {
        await logout();
        setUser(null);
      },
      { message: 'Keluar…' },
    );
  };

  return (
    <AuthContext.Provider value={{ user, loading, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
