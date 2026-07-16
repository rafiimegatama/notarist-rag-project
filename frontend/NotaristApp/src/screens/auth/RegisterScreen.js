import React, { useMemo, useState } from 'react';
import { View, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import TextField from '../../components/TextField';
import Button from '../../components/Button';
import Banner from '../../components/Banner';
import { useTheme } from '../../context/ThemeContext';
import { RegisterService, EndpointUnavailableError } from '../../services/RegisterService';
import * as V from '../../utils/validation';
import { APP } from '../../constants/config';

/**
 * Account registration. Full production UI (fields, validation, disabled submit, loading, error).
 * The backend has NO register endpoint: RegisterService.available is false, so submit stays
 * disabled behind a clear banner and no phantom request is ever sent. When the endpoint exists,
 * flipping FEATURES.registerEndpoint enables the exact same screen.
 */
export default function RegisterScreen({ navigation }) {
  const theme = useTheme();
  const available = RegisterService.available;

  const [form, setForm] = useState({ fullName: '', username: '', email: '', password: '', confirm: '' });
  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const set = (k) => (v) => setForm((f) => ({ ...f, [k]: v }));
  const touch = (k) => () => setTouched((t) => ({ ...t, [k]: true }));

  const errors = useMemo(() => {
    const { errors } = V.runValidators({
      fullName: () => V.required(form.fullName, 'Nama lengkap'),
      username: () => V.username(form.username),
      email: () => V.email(form.email),
      password: () => V.password(form.password),
      confirm: () => V.matches(form.confirm, form.password),
    });
    return errors;
  }, [form]);

  const isValid = Object.keys(errors).length === 0;
  const submitDisabled = !available || !isValid || submitting;

  const handleSubmit = async () => {
    setSubmitError(null);
    setSubmitting(true);
    try {
      await RegisterService.register({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      // Not reachable today (service throws). Left for when the endpoint exists.
      navigation.goBack();
    } catch (err) {
      const msg = err instanceof EndpointUnavailableError
        ? err.message
        : (err.response?.data?.errorMessage || 'Pendaftaran gagal. Coba lagi.');
      setSubmitError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const showError = (k) => (touched[k] ? errors[k] : undefined);

  return (
    <Screen scroll keyboardAware>
      <View style={{ marginBottom: theme.spacing.xl, marginTop: theme.spacing.sm }}>
        <AppText style={{ fontSize: 40 }}>⚖️</AppText>
        <AppText variant="h1" style={{ marginTop: theme.spacing.sm }}>Buat Akun</AppText>
        <AppText color="textMuted" variant="bodySm" style={{ marginTop: 4 }}>
          Daftar untuk mengakses {APP.fullName}
        </AppText>
      </View>

      {!available ? (
        <Banner
          variant="warning"
          title="Backend endpoint unavailable"
          message="Pendaftaran mandiri belum didukung server. Formulir aktif, namun tombol Daftar dinonaktifkan hingga endpoint tersedia. Hubungi administrator untuk pembuatan akun."
          style={{ marginBottom: theme.spacing.lg }}
        />
      ) : null}

      {submitError ? (
        <Banner variant="danger" title="Gagal" message={submitError} style={{ marginBottom: theme.spacing.lg }} />
      ) : null}

      <TextField
        label="Nama Lengkap"
        value={form.fullName}
        onChangeText={set('fullName')}
        onBlur={touch('fullName')}
        placeholder="Mis. Budi Santoso"
        autoCapitalize="words"
        error={showError('fullName')}
      />
      <TextField
        label="Username"
        value={form.username}
        onChangeText={set('username')}
        onBlur={touch('username')}
        placeholder="username"
        error={showError('username')}
      />
      <TextField
        label="Email"
        value={form.email}
        onChangeText={set('email')}
        onBlur={touch('email')}
        placeholder="nama@kantor.co.id"
        keyboardType="email-address"
        error={showError('email')}
      />
      <TextField
        label="Password"
        value={form.password}
        onChangeText={set('password')}
        onBlur={touch('password')}
        placeholder="Minimal 8 karakter"
        secureTextEntry
        error={showError('password')}
      />
      <TextField
        label="Konfirmasi Password"
        value={form.confirm}
        onChangeText={set('confirm')}
        onBlur={touch('confirm')}
        placeholder="Ulangi password"
        secureTextEntry
        error={showError('confirm')}
      />

      <Button
        title={available ? 'Daftar' : 'Daftar (tidak tersedia)'}
        onPress={handleSubmit}
        disabled={submitDisabled}
        loading={submitting}
        style={{ marginTop: theme.spacing.sm }}
      />

      <TouchableOpacity onPress={() => navigation.goBack()} style={{ marginTop: theme.spacing.lg, alignItems: 'center' }}>
        <AppText color="textMuted" variant="bodySm">
          Sudah punya akun? <AppText style={{ color: theme.colors.primary }}>Masuk</AppText>
        </AppText>
      </TouchableOpacity>
    </Screen>
  );
}
