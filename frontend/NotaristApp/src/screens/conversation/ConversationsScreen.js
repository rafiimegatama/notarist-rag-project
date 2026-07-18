import React, { useCallback, useMemo, useState } from 'react';
import { View, SectionList, RefreshControl, TouchableOpacity, Alert } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import Card from '../../components/Card';
import MockBanner from '../../components/MockBanner';
import SearchBar from '../../components/SearchBar';
import DangerDialog from '../../components/DangerDialog';
import PromptDialog from '../../components/PromptDialog';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { useTheme } from '../../context/ThemeContext';
import { relativeTime } from '../../utils/format';
import { groupConversations } from '../../models/Conversation';
import usePolledResource from '../../hooks/usePolledResource';
import { useConversations } from '../../state';

const UNTITLED = 'Percakapan tanpa judul';

// Case-insensitive match over the (possibly renamed) title and the last message. Pure so the search
// is a simple derive over the list, not a second source of truth.
function matches(conversation, needle) {
  if (!needle) return true;
  const hay = `${conversation.title || ''} ${conversation.lastMessage || ''}`.toLowerCase();
  return hay.includes(needle);
}

// Build the section list: pinned conversations float to the top in their own section, everything else
// keeps the Today / Yesterday / … date grouping. Search filters BEFORE splitting, so a pinned match
// still leads and the date groups only hold what matched.
function buildSections(conversations, query) {
  const needle = query.trim().toLowerCase();
  const filtered = needle ? conversations.filter((c) => matches(c, needle)) : conversations;

  const pinned = filtered
    .filter((c) => c.pinned)
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0));
  const rest = filtered.filter((c) => !c.pinned);

  const sections = [];
  if (pinned.length) sections.push({ key: 'pinned', title: '📌 Disematkan', data: pinned });
  for (const s of groupConversations(rest)) sections.push(s);
  return sections;
}

export default function ConversationsScreen({ navigation }) {
  const theme = useTheme();
  const { conversations, loading, refreshing, error, offline, usingMock, refresh, remove, togglePin, rename } = useConversations();
  // Arm focus-refresh + polling for the conversation list while this screen is visible. The context
  // registers the refresher; without a subscriber the registration was inert (see ReminderScreen).
  usePolledResource('conversations');
  const [query, setQuery] = useState('');
  const [pendingDelete, setPendingDelete] = useState(null); // conversation awaiting delete confirmation
  const [pendingRename, setPendingRename] = useState(null); // conversation awaiting rename

  const sections = useMemo(() => buildSections(conversations, query), [conversations, query]);
  const searching = query.trim().length > 0;

  const open = useCallback((item) => {
    // Opening loads the conversation in the Assistant tab by sessionId; AssistantScreen rehydrates
    // from the passed sessionId (Sprint 3).
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

  const confirmRename = (title) => {
    const item = pendingRename;
    setPendingRename(null);
    if (item) rename(item.sessionId, title);
  };

  // Stable identities so ConversationRow's memo holds; each handler takes the item, so the parent
  // passes ONE function to every row instead of a per-row closure.
  const renderItem = useCallback(
    ({ item }) => (
      <ConversationRow
        item={item}
        onOpen={open}
        onTogglePin={togglePin}
        onRequestRename={setPendingRename}
        onRequestDelete={setPendingDelete}
      />
    ),
    [open, togglePin],
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
      {/* Fixed header — search + mock notice. Kept OUT of the SectionList header so the input never
          loses focus when the list re-renders as results narrow. */}
      <View style={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.md, gap: theme.spacing.md }}>
        {usingMock ? (
          <MockBanner message="Data contoh — daftar percakapan belum tersedia (hanya riwayat per-sesi yang didukung backend). Sematan & nama tersimpan di perangkat." />
        ) : null}
        <SearchBar
          value={query}
          onChangeText={setQuery}
          placeholder="Cari percakapan…"
        />
      </View>

      <SectionList
        sections={sections}
        keyExtractor={keyExtractor}
        renderSectionHeader={renderSectionHeader}
        renderItem={renderItem}
        keyboardShouldPersistTaps="handled"
        ListEmptyComponent={
          searching
            ? <EmptyState icon="🔍" title="Tidak ada hasil" description={`Tidak ada percakapan yang cocok dengan "${query.trim()}".`} />
            : <EmptyState icon="💬" title="Belum ada percakapan" description="Percakapan dengan Asisten akan muncul di sini." />
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
        contentContainerStyle={{ flexGrow: 1, paddingTop: theme.spacing.sm, paddingBottom: theme.spacing.lg }}
      />

      <DangerDialog
        visible={!!pendingDelete}
        title="Hapus Percakapan"
        message={pendingDelete ? `Hapus "${pendingDelete.title || UNTITLED}"? Tindakan ini tidak dapat dibatalkan.` : ''}
        confirmLabel="Hapus"
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
      <PromptDialog
        visible={!!pendingRename}
        title="Ganti Nama Percakapan"
        message={
          pendingRename && pendingRename.originalTitle && pendingRename.title !== pendingRename.originalTitle
            ? `Nama asli: ${pendingRename.originalTitle}`
            : 'Nama tersimpan di perangkat ini.'
        }
        initialValue={pendingRename ? (pendingRename.title || '') : ''}
        placeholder="Nama percakapan"
        confirmLabel="Simpan"
        onConfirm={confirmRename}
        onCancel={() => setPendingRename(null)}
      />
    </Screen>
  );
}

/**
 * One conversation row: a leading pin toggle, the tappable title/preview/time, and rename + delete
 * actions. Memoized (Sprint 4, Task 10) — every handler takes the item so the parent passes stable
 * functions, and relativeTime() only re-runs when the row's own data changes.
 */
const ConversationRow = React.memo(function ConversationRow({ item, onOpen, onTogglePin, onRequestRename, onRequestDelete }) {
  const theme = useTheme();
  const title = item.title || UNTITLED;
  const action = {
    minWidth: theme.touchTarget.min,
    minHeight: theme.touchTarget.min,
    alignItems: 'center',
    justifyContent: 'center',
  };
  return (
    <View style={{ paddingHorizontal: theme.spacing.lg, paddingBottom: theme.spacing.md }}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <TouchableOpacity
            onPress={() => onTogglePin(item.sessionId)}
            accessibilityRole="button"
            accessibilityState={{ selected: !!item.pinned }}
            accessibilityLabel={item.pinned ? `Lepas sematan ${title}` : `Sematkan ${title}`}
            hitSlop={theme.hitSlop}
            style={[action, { marginRight: theme.spacing.xs }]}
          >
            <AppText style={{ fontSize: 16, opacity: item.pinned ? 1 : 0.3 }}>📌</AppText>
          </TouchableOpacity>

          <TouchableOpacity
            style={{ flex: 1 }}
            onPress={() => onOpen(item)}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel={`Buka percakapan ${title}`}
          >
            <AppText variant="bodyStrong" numberOfLines={1}>{title}</AppText>
            {item.lastMessage ? (
              <AppText variant="bodySm" color="textFaint" numberOfLines={1} style={{ marginTop: 2 }}>{item.lastMessage}</AppText>
            ) : null}
            <AppText variant="micro" color="textFaint" style={{ marginTop: 4 }}>{relativeTime(item.updatedAt)}</AppText>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => onRequestRename(item)}
            accessibilityRole="button"
            accessibilityLabel={`Ganti nama percakapan ${title}`}
            hitSlop={theme.hitSlop}
            style={action}
          >
            <AppText style={{ fontSize: 16 }}>✏️</AppText>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => onRequestDelete(item)}
            accessibilityRole="button"
            accessibilityLabel={`Hapus percakapan ${title}`}
            hitSlop={theme.hitSlop}
            style={action}
          >
            <AppText style={{ fontSize: 16 }}>🗑️</AppText>
          </TouchableOpacity>
        </View>
      </Card>
    </View>
  );
});
