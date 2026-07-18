import React from 'react';
import { View } from 'react-native';
import Screen from '../../components/Screen';
import Card from '../../components/Card';
import AppText from '../../components/AppText';
import Avatar from '../../components/Avatar';
import SectionHeader from '../../components/SectionHeader';
import SettingItem from '../../components/SettingItem';
import Divider from '../../components/Divider';
import Button from '../../components/Button';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useUser from '../../hooks/useUser';
import useAsync from '../../hooks/useAsync';
import { listDocuments } from '../../api/documents';
import { getStoredSessionId } from '../../api/auth';
import { formatDateTime, EMPTY } from '../../utils/format';

/**
 * Production profile page. Everything is sourced from the auth context + JWT claims (no hardcoded
 * values) and degrades to "—" when a field is unavailable. Email/avatar have no backend today
 * (no /auth/me) and therefore show placeholders. The document statistic uses the existing
 * GET /documents endpoint and has its own loading/error handling.
 */
export default function ProfileScreen({ navigation }) {
  const theme = useTheme();
  const user = useUser();

  // Live statistic from an EXISTING endpoint. Never invents a number.
  //
  // Reads `.page`, not `.data.page`: api/documents#listDocuments returns a normalized { items, page }
  // as of Sprint 6 rather than the raw axios body. `totalElements` stays undefined when the server
  // omits it and val() renders "—"; the count is never inferred from the page we happen to hold.
  const stats = useAsync(() => listDocuments(0, 1), []);
  const totalDocuments = stats.data?.page?.totalElements;

  // After a relaunch the session id lives only in storage (it is not a JWT claim), so fall back to
  // it rather than rendering an empty Session field.
  const storedSession = useAsync(getStoredSessionId, []);
  const sessionId = user?.sessionId || storedSession.data;

  const val = (v) => (v == null || v === '' ? EMPTY : String(v));

  if (!user) {
    return (
      <Screen>
        <AppText color="textMuted">Sesi tidak tersedia.</AppText>
      </Screen>
    );
  }

  return (
    <Screen scroll padded>
      {/* Identity header */}
      <Card style={{ alignItems: 'center' }}>
        <Avatar name={user.displayName} size={72} />
        <AppText variant="h2" style={{ marginTop: theme.spacing.md }}>{user.displayName}</AppText>
        {user.primaryRoleLabel ? (
          <View style={{ marginTop: 6, backgroundColor: theme.colors.surfaceAlt, borderRadius: theme.radius.pill, paddingHorizontal: 12, paddingVertical: 4 }}>
            <AppText variant="caption" color="primary">{user.primaryRoleLabel}</AppText>
          </View>
        ) : null}
        <AppText color="textFaint" variant="bodySm" style={{ marginTop: 6 }}>@{val(user.username)}</AppText>
      </Card>

      {/* Account info */}
      <SectionHeader title="Informasi Akun" />
      <Card padded={false}>
        <SettingItem title="Nama Lengkap" value={val(user.fullName)} />
        <Divider />
        <SettingItem title="Username" value={val(user.username)} />
        <Divider />
        <SettingItem title="Email" value={user.email ? user.email : 'Tidak tersedia'} />
        <Divider />
        <SettingItem title="Peran" value={user.roles?.length ? user.roles.join(', ') : EMPTY} />
        <Divider />
        <SettingItem title="Tenant" value={val(user.tenantId)} />
        <Divider />
        <SettingItem title="ID Pengguna" value={val(user.userId)} />
      </Card>

      {/* Statistics */}
      <SectionHeader title="Statistik" />
      <Card padded={false}>
        <SettingItem
          icon="📄"
          title="Total Dokumen"
          rightElement={
            stats.loading ? (
              <Skeleton width={40} height={16} />
            ) : (
              <AppText variant="bodyStrong" color={stats.error ? 'textFaint' : 'text'}>
                {stats.error ? EMPTY : val(totalDocuments)}
              </AppText>
            )
          }
        />
      </Card>

      {/* Session */}
      <SectionHeader title="Sesi" />
      <Card padded={false}>
        <SettingItem title="Login Terakhir" value={user.issuedAt ? formatDateTime(user.issuedAt) : EMPTY} />
        <Divider />
        <SettingItem title="Token Berlaku Hingga" value={user.expiresAt ? formatDateTime(user.expiresAt) : EMPTY} />
        <Divider />
        <SettingItem title="ID Sesi" value={val(sessionId)} />
      </Card>

      <Button
        title="Pengaturan"
        icon="⚙️"
        variant="secondary"
        onPress={() => navigation.navigate('Settings')}
        style={{ marginTop: theme.spacing.xl }}
      />
      <View style={{ height: theme.spacing.xxl }} />
    </Screen>
  );
}
