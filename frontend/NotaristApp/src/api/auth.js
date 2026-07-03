import client from './client';
import * as SecureStore from 'expo-secure-store';

export async function login(username, password) {
  const response = await client.post('/auth/login', { username, password });
  const { token, refreshToken, expiresAt } = response.data.data;
  await SecureStore.setItemAsync('jwt_token', token);
  if (refreshToken) {
    await SecureStore.setItemAsync('refresh_token', refreshToken);
  }
  return response.data.data;
}

export async function logout() {
  try {
    await client.post('/auth/logout');
  } catch (_) {
    // ignore server errors on logout
  } finally {
    await SecureStore.deleteItemAsync('jwt_token');
    await SecureStore.deleteItemAsync('refresh_token');
  }
}

export async function getStoredToken() {
  return SecureStore.getItemAsync('jwt_token');
}
