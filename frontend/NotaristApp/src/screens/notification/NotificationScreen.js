import React from 'react';
import { View, FlatList, TouchableOpacity, RefreshControl } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import Card from '../../components/Card';
import Badge from '../../components/Badge';
import Banner from '../../components/Banner';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useNotifications from '../../hooks/useNotifications';
import { relativeTime } from '../../utils/format';

function NotificationRow({ item, onPress }) {
  const theme = useTheme();
  return (
    <TouchableOpacity activeOpacity={0.7} onPress={() => onPress(item)}>
      <Card style={{ marginBottom: theme.spacing.md, flexDirection: 'row', alignItems: 'flex-start', opacity: item.read ? 0.6 : 1 }}>
        <AppText style={{ fontSize: 20, marginRight: theme.spacing.md }}>{item.icon}</AppText>
        <View style={{ flex: 1 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <AppText variant="bodyStrong" style={{ flex: 1 }} numberOfLines={1}>{item.title}</AppText>
            {!item.read ? <Badge dot /> : null}
          </View>
          {item.body ? (
            <AppText color="textMuted" variant="bodySm" style={{ marginTop: 4 }} numberOfLines={2}>{item.body}</AppText>
          ) : null}
          <AppText color="textFaint" variant="micro" style={{ marginTop: 6 }}>{relativeTime(item.createdAt)}</AppText>
        </View>
      </Card>
    </TouchableOpacity>
  );
}

export default function NotificationScreen() {
  const theme = useTheme();
  const { items, unread, backendAvailable, loading, refreshing, error, reload, refresh, markAllRead, markRead } = useNotifications();

  const Header = (
    <View>
      {!backendAvailable ? (
        <Banner
          variant="info"
          title="Belum terhubung"
          message="Layanan notifikasi belum tersedia di server. Daftar akan terisi otomatis begitu endpoint aktif."
          style={{ marginBottom: theme.spacing.lg }}
        />
      ) : null}
      <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: theme.spacing.md }}>
        <AppText variant="h2" style={{ flex: 1 }}>Notifikasi</AppText>
        <Badge count={unread} style={{ marginRight: theme.spacing.md }} />
        {unread > 0 ? (
          <TouchableOpacity onPress={markAllRead}>
            <AppText variant="bodySm" style={{ color: theme.colors.primary }}>Tandai dibaca</AppText>
          </TouchableOpacity>
        ) : null}
      </View>
    </View>
  );

  if (loading) {
    return (
      <Screen padded={false}>
        <View style={{ padding: theme.spacing.lg }}>{Header}</View>
        <SkeletonList count={5} />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        {Header}
        <ErrorState message="Gagal memuat notifikasi." onRetry={reload} />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <NotificationRow item={item} onPress={(n) => markRead(n.id)} />}
        ListHeaderComponent={<View style={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.lg }}>{Header}</View>}
        contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingBottom: theme.spacing.xxl, flexGrow: 1 }}
        ListEmptyComponent={
          <EmptyState
            icon="🔔"
            title="Tidak ada notifikasi"
            description="Anda akan melihat pemberitahuan sistem, dokumen, dan keamanan di sini."
          />
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
      />
    </Screen>
  );
}
