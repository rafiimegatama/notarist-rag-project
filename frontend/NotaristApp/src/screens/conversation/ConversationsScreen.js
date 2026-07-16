import React, { useCallback, useMemo, useState } from 'react';
import { View, SectionList, RefreshControl, TouchableOpacity, Alert } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import Card from '../../components/Card';
import MockBanner from '../../components/MockBanner';
import DangerDialog from '../../components/DangerDialog';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { useTheme } from '../../context/ThemeContext';
import { relativeTime } from '../../utils/format';
import { groupConversations } from '../../models/Conversation';
import { useConversations } from '../../state';

export default function ConversationsScreen({ navigation }) {
  const theme = useTheme();
  const { conversations, loading, refreshing, error, offline, usingMock, refresh, remove } = useConversations();
  const [pendingDelete, setPendingDelete] = useState(null); // conversation awaiting delete confirmation

  // Memoized: grouping walks and buckets the whole list, and it ran on every render — including the
  // ones caused by opening the delete dialog (Sprint 4, Task 10). It also handed SectionList a new
  // `sections` array each time, forcing a full re-diff.
  const sections = useMemo(() => groupConversations(conversations), [conversations]);

  const open = useCallback((item) => {
    // Opening loads the conversation in the Assistant tab by sessionId. The Assistant screen may not
    // yet rehydrate from a passed sessionId — tracked as Sprint-3 debt.
    navigation.navigate('Main', { screen: 'Asisten', params: { sessionId: item.sessionId } });
  }, [navigation]);

  const confirmDelete = async () => {
    const item = pendingDelete;
    setPendingDelete(null);
    if (item) {
      try { await remove(item.sessionId); }
      catch (_) { Alert.alert('Gagal', 'Tidak dapat menghapus percakapan.'); }
    }
  };

  // Stable identity so ConversationRow's memo holds; the row was previously an inline closure over
  // `open` and `setPendingDelete`, re-created on every render of this screen.
  const renderItem = useCallback(
    ({ item }) => <ConversationRow item={item} onOpen={open} onRequestDelete={setPendingDelete} />,
    [open],
  );

  const keyExtractor = useCallback((item) => item.id, []);

  const renderSectionHeader = useCallback(({ section }) => (
    <AppText
      variant="label"
      color="textMuted"
      style={{
        paddingHorizontal: theme.spacing.lg,
        paddingTop: theme.spacing.lg,
        paddingBottom: theme.spacing.sm,
        textTransform: 'uppercase',
        letterSpacing: 0.8,
      }}
    >
      {section.title}
    </AppText>
  ), [theme]);

  if (loading && !conversations.length) return <Screen padded={false}><LoadingSkeleton count={6} /></Screen>;
  if (error && !conversations.length) {
    return <Screen><ErrorState error={error} title={offline ? 'Tidak ada koneksi' : 'Gagal memuat'} message="Tidak dapat memuat riwayat percakapan." onRetry={refresh} /></Screen>;
  }

  return (
    <Screen padded={false}>
      <SectionList
        sections={sections}
        keyExtractor={keyExtractor}
        renderSectionHeader={renderSectionHeader}
        renderItem={renderItem}
        ListHeaderComponent={
          usingMock ? (
            <View style={{ padding: theme.spacing.lg, paddingBottom: 0 }}>
              <MockBanner message="Data contoh — daftar percakapan belum tersedia (hanya riwayat per-sesi yang didukung backend)." />
            </View>
          ) : null
        }
        ListEmptyComponent={<EmptyState icon="💬" title="Belum ada percakapan" description="Percakapan dengan Asisten akan muncul di sini." />}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
        contentContainerStyle={{ flexGrow: 1, paddingBottom: theme.spacing.lg }}
      />
      <DangerDialog
        visible={!!pendingDelete}
        title="Hapus Percakapan"
        message={pendingDelete ? `Hapus "${pendingDelete.title}"? Tindakan ini tidak dapat dibatalkan.` : ''}
        confirmLabel="Hapus"
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </Screen>
  );
}

/**
 * One conversation row. Lifted out of the inline renderItem and memoized (Sprint 4, Task 10): it was
 * re-rendering for every row on every screen render, each one re-running relativeTime().
 *
 * Both handlers take the item, so the parent can pass ONE stable function to every row instead of a
 * per-row closure — the same reasoning as CaseCard's onPress.
 */
const ConversationRow = React.memo(function ConversationRow({ item, onOpen, onRequestDelete }) {
  const theme = useTheme();
  return (
    <View style={{ paddingHorizontal: theme.spacing.lg, paddingBottom: theme.spacing.md }}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <TouchableOpacity
            style={{ flex: 1 }}
            onPress={() => onOpen(item)}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel={`Buka percakapan ${item.title}`}
          >
            <AppText variant="bodyStrong" numberOfLines={1}>{item.title}</AppText>
            <AppText variant="bodySm" color="textFaint" numberOfLines={1} style={{ marginTop: 2 }}>{item.lastMessage}</AppText>
            <AppText variant="micro" color="textFaint" style={{ marginTop: 4 }}>{relativeTime(item.updatedAt)}</AppText>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => onRequestDelete(item)}
            accessibilityRole="button"
            accessibilityLabel={`Hapus percakapan ${item.title}`}
            hitSlop={theme.hitSlop}
            style={{
              paddingLeft: theme.spacing.md,
              minWidth: theme.touchTarget.min,
              minHeight: theme.touchTarget.min,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <AppText style={{ fontSize: 18 }}>🗑️</AppText>
          </TouchableOpacity>
        </View>
      </Card>
    </View>
  );
});
