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
    if (error.response?.status === 401) {
      await SecureStore.deleteItemAsync('jwt_token');
    }
    return Promise.reject(error);
  }
);

export default client;
