import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

// Change this to your backend IP when running on a physical device
const BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://10.0.2.2:8080/api/v1';

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

let authFailureHandler = null;
export function setAuthFailureHandler(handler) {
  authFailureHandler = handler;
}

async function clearSession() {
  await SecureStore.deleteItemAsync('jwt_token');
  await SecureStore.deleteItemAsync('refresh_token');
  await SecureStore.deleteItemAsync('session_id');
}

let refreshPromise = null;

async function performRefresh() {
  const refreshToken = await SecureStore.getItemAsync('refresh_token');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const response = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
  const { accessToken, refreshToken: newRefreshToken, sessionId } = response.data.data;
  await SecureStore.setItemAsync('jwt_token', accessToken);
  if (newRefreshToken) {
    await SecureStore.setItemAsync('refresh_token', newRefreshToken);
  }
  if (sessionId) {
    await SecureStore.setItemAsync('session_id', sessionId);
  }
  return accessToken;
}

client.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest?.url?.includes('/auth/');

    if (error.response?.status === 401 && !isAuthEndpoint && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        if (!refreshPromise) {
          refreshPromise = performRefresh().finally(() => {
            refreshPromise = null;
          });
        }
        const newToken = await refreshPromise;
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        await clearSession();
        authFailureHandler?.();
        return Promise.reject(error);
      }
    }

    if (error.response?.status === 401 && isAuthEndpoint) {
      await clearSession();
      authFailureHandler?.();
    }

    return Promise.reject(error);
  }
);

export default client;
