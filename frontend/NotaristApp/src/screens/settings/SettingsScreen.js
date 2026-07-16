import React from 'react';
import { View, Switch, Alert, Linking, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import Card from '../../components/Card';
import AppText from '../../components/AppText';
import SectionHeader from '../../components/SectionHeader';
import SettingItem from '../../components/SettingItem';
import Divider from '../../components/Divider';
import { useTheme } from '../../context/ThemeContext';
import { usePreferences } from '../../context/PreferencesContext';
import { useAuth } from '../../context/AuthContext';
import { APP, LINKS, PLATFORM_LABEL, FEATURES } from '../../constants/config';
import { BASE_URL } from '../../api/client';

// Inline chip selector (kept local to Settings — a single, simple control).
function ChipGroup({ options, value, onChange }) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, padding: theme.spacing.lg, paddingTop: theme.spacing.sm }}>
      {options.map((opt) => {
        const active = opt.value === value;
        return (
          <TouchableOpacity
            key={String(opt.value)}
            onPress={() => onChange(opt.value)}
            style={{
              paddingHorizontal: 14,
              paddingVertical: 8,
              borderRadius: theme.radius.pill,
              borderWidth: 1,
              backgroundColor: active ? theme.colors.primary : theme.colors.surfaceAlt,
              borderColor: active ? theme.colors.primary : theme.colors.border,
            }}
          >
            <AppText variant="bodySm" style={{ color: active ? '#fff' : theme.colors.textMuted, fontWeight: active ? '600' : '400' }}>
              {opt.label}
            </AppText>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const cycle = (arr, current) => arr[(arr.indexOf(current) + 1) % arr.length];

export default function SettingsScreen({ navigation }) {
  const theme = useTheme();
  const prefs = usePreferences();
  const { signOut } = useAuth();

  const openLink = (url) => Linking.openURL(url).catch(() => Alert.alert('Tidak dapat membuka tautan', url));

  const confirmLogout = () => {
    Alert.alert('Keluar', 'Anda yakin ingin keluar dari akun ini?', [
      { text: 'Batal', style: 'cancel' },
      { text: 'Keluar', style: 'destructive', onPress: () => signOut() },
    ]);
  };

  return (
    <Screen scroll padded>
      {/* General */}
      <SectionHeader title="Umum" />
      <Card padded={false}>
        <SettingItem icon="👤" title="Profil" subtitle="Informasi akun Anda" onPress={() => navigation.navigate('Profile')} />
        <Divider inset={56} />
        <SettingItem icon="🔔" title="Notifikasi" subtitle="Pemberitahuan sistem" onPress={() => navigation.navigate('Notification')} />
      </Card>

      {/* Appearance */}
      <SectionHeader title="Tampilan" />
      <Card padded={false}>
        <SettingItem icon="🎨" title="Tema" subtitle="Ikuti sistem, terang, atau gelap" />
        <ChipGroup
          value={prefs.themeMode}
          onChange={prefs.setThemeMode}
          options={[
            { label: 'Sistem', value: 'system' },
            { label: 'Terang', value: 'light' },
            { label: 'Gelap', value: 'dark' },
          ]}
        />
      </Card>

      {/* Language */}
      <SectionHeader title="Bahasa" />
      <Card padded={false}>
        <SettingItem icon="🌐" title="Bahasa Aplikasi" subtitle="Preferensi bahasa (pelokalan penuh menyusul)" />
        <ChipGroup
          value={prefs.language}
          onChange={prefs.setLanguage}
          options={[
            { label: 'Indonesia', value: 'id' },
            { label: 'English', value: 'en' },
          ]}
        />
      </Card>

      {/* AI Runtime */}
      <SectionHeader title="AI Runtime" />
      <Card padded={false}>
        <SettingItem
          icon="🛡️"
          title="Mode Keamanan"
          subtitle="Ketat menolak jawaban dengan grounding rendah"
          value={prefs.aiSafetyMode}
          onPress={() => prefs.setAiSafetyMode(cycle(['STRICT', 'BALANCED', 'PERMISSIVE'], prefs.aiSafetyMode))}
        />
        <Divider inset={56} />
        <SettingItem
          icon="🔎"
          title="Maksimum Hasil"
          subtitle="Jumlah kutipan yang diambil"
          value={String(prefs.aiMaxResults)}
          onPress={() => prefs.setAiMaxResults(cycle([5, 10, 20], prefs.aiMaxResults))}
        />
        <Divider inset={56} />
        <SettingItem icon="ℹ️" title="Preferensi tersimpan lokal" subtitle="Akan diterapkan ke Asisten pada sprint berikutnya" />
      </Card>

      {/* Server */}
      <SectionHeader title="Server" />
      <Card padded={false}>
        <SettingItem icon="🌍" title="API Base URL" subtitle={BASE_URL} />
        <Divider inset={56} />
        <SettingItem
          icon="⚙️"
          title="Konfigurasi Endpoint"
          subtitle="Diatur via EXPO_PUBLIC_API_URL saat build — tidak dapat diubah saat runtime"
        />
      </Card>

      {/* About */}
      <SectionHeader title="Tentang" />
      <Card padded={false}>
        <SettingItem icon="📦" title="Versi" value={`${APP.version} (${APP.build})`} />
        <Divider inset={56} />
        <SettingItem icon="📱" title="Platform" value={PLATFORM_LABEL} />
        <Divider inset={56} />
        <SettingItem icon="✉️" title="Dukungan" subtitle="Hubungi tim dukungan" onPress={() => openLink(LINKS.support)} />
      </Card>

      {/* Legal */}
      <SectionHeader title="Legal" />
      <Card padded={false}>
        <SettingItem icon="📄" title="Ketentuan Layanan" onPress={() => openLink(LINKS.terms)} />
        <Divider inset={56} />
        <SettingItem icon="🔏" title="Kebijakan Privasi" onPress={() => openLink(LINKS.privacyPolicy)} />
      </Card>

      {/* Privacy */}
      <SectionHeader title="Privasi" />
      <Card padded={false}>
        <SettingItem
          icon="📊"
          title="Analitik Penggunaan"
          subtitle="Bantu tingkatkan aplikasi (lokal)"
          rightElement={
            <Switch
              value={prefs.analyticsEnabled}
              onValueChange={prefs.setAnalyticsEnabled}
              trackColor={{ true: theme.colors.primary, false: theme.colors.borderStrong }}
            />
          }
        />
      </Card>

      {/* Developer — only visible when the playground feature flag is enabled. */}
      {FEATURES.devPlayground ? (
        <>
          <SectionHeader title="Developer" />
          <Card padded={false}>
            <SettingItem icon="🧩" title="Component Playground" subtitle="Showcase seluruh komponen UI" onPress={() => navigation.navigate('Playground')} />
          </Card>
        </>
      ) : null}

      {/* Logout */}
      <SectionHeader title="Sesi" />
      <Card padded={false}>
        <SettingItem icon="🚪" title="Keluar" danger onPress={confirmLogout} />
      </Card>

      <View style={{ height: theme.spacing.xxl }} />
    </Screen>
  );
}
