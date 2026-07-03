import React, { createContext, useContext, useState, useEffect } from 'react';
import { login, logout, getStoredToken } from '../api/auth';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const token = await getStoredToken();
      if (token) {
        setUser({ token });
      }
      setLoading(false);
    })();
  }, []);

  const signIn = async (username, password) => {
    const data = await login(username, password);
    setUser({ token: data.token, ...data });
    return data;
  };

  const signOut = async () => {
    await logout();
    setUser(null);
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
