import { useMemo } from 'react';
import { useAuth } from '../context/AuthContext';
import { buildUser } from '../models/User';

/**
 * Derives the User view model from the auth context (login TokenResponse + JWT claims). Returns
 * null when signed out. Screens read this instead of decoding tokens themselves.
 */
export default function useUser() {
  const { user } = useAuth();
  return useMemo(() => buildUser(user), [user]);
}
