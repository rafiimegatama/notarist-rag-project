import React, { useState } from 'react';
import { View, TouchableOpacity } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import Screen from '../components/Screen';
import Card from '../components/Card';
import AppText from '../components/AppText';
import TextField from '../components/TextField';
import Button from '../components/Button';
import Banner from '../components/Banner';
import { APP } from '../constants/config';

/**
 * Sign-in screen. The auth flow itself is unchanged — it still calls AuthContext.signIn(), which
 * owns token storage. Only the presentation changed: themed components, inline error state instead
 * of a modal Alert, and a submit disabled until both fields are filled.
 */
export default function LoginScreen({ navigation }) {
  const { signIn } = useAuth();
  const theme = useTheme();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const canSubmit = username.trim().length > 0 && password.length > 0 && !loading;

  const handleLogin = async () => {
    if (!canSubmit) return;
    setError(null);
    setLoading(true);
    try {
      await signIn(username.trim(), password);
    } catch (err) {
      setError(err.response?.data?.errorMessage || 'Login gagal. Periksa kredensial Anda.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen scroll keyboardAware contentContainerStyle={{ justifyContent: 'center' }}>
      <View style={{ alignItems: 'center', marginBottom: theme.spacing.xl }}>
        <AppText style={{ fontSize: 44 }}>⚖️</AppText>
        <AppText variant="h1" style={{ marginTop: theme.spacing.sm }}>{APP.name}</AppText>
        <AppText color="textMuted" variant="bodySm" style={{ marginTop: theme.spacing.xs }}>
          {APP.tagline}
        </AppText>
      </View>

      <Card>
        {error ? (
          <Banner
            variant="danger"
            title="Login Gagal"
            message={error}
            style={{ marginBottom: theme.spacing.lg }}
          />
        ) : null}

        <TextField
          label="Username"
          value={username}
          onChangeText={setUsername}
          placeholder="username"
          editable={!loading}
          returnKeyType="next"
        />
        <TextField
          label="Password"
          value={password}
          onChangeText={setPassword}
          placeholder="Password"
          secureTextEntry
          editable={!loading}
          returnKeyType="go"
          onSubmitEditing={handleLogin}
        />

        <Button
          title="Masuk"
          onPress={handleLogin}
          disabled={!canSubmit}
          loading={loading}
          style={{ marginTop: theme.spacing.sm }}
        />

        <TouchableOpacity
          onPress={() => navigation?.navigate('Register')}
          style={{ marginTop: theme.spacing.lg, alignItems: 'center' }}
        >
          <AppText color="textMuted" variant="bodySm">
            Belum punya akun? <AppText style={{ color: theme.colors.primary }}>Daftar</AppText>
          </AppText>
        </TouchableOpacity>
      </Card>
    </Screen>
  );
}
