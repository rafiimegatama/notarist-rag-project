import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View,
  TextInput,
  FlatList,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { askAssistant, getConversationHistory } from '../api/assistant';
import { useTheme } from '../context/ThemeContext';
import useThemedStyles from '../hooks/useThemedStyles';
import AppText from '../components/AppText';

const SESSION_STORE_KEY = 'assistant_session_id';

// Session id must be persisted, not regenerated per mount — a fresh id every
// time means getConversationHistory() can never find a matching prior session.
async function getOrCreateSessionId() {
  let id = await SecureStore.getItemAsync(SESSION_STORE_KEY);
  if (!id) {
    id = Crypto.randomUUID();
    await SecureStore.setItemAsync(SESSION_STORE_KEY, id);
  }
  return id;
}

function turnsToMessages(turns) {
  const sorted = [...turns].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
  const messages = [];
  for (const turn of sorted) {
    messages.push({ id: `${turn.turnId}-q`, role: 'user', content: turn.userQuery });
    messages.push({ id: `${turn.turnId}-a`, role: 'assistant', content: turn.assistantAnswer });
  }
  return messages;
}

function MessageBubble({ message, styles }) {
  const isUser = message.role === 'user';
  return (
    <View style={[styles.bubbleRow, isUser && styles.bubbleRowUser]}>
      {!isUser && <AppText style={styles.avatar}>🤖</AppText>}
      <View style={[styles.bubble, isUser ? styles.userBubble : styles.aiBubble]}>
        <AppText style={[styles.bubbleText, isUser && styles.userBubbleText]}>
          {message.content}
        </AppText>
        {message.citations && message.citations.length > 0 && (
          <View style={styles.citations}>
            <AppText style={styles.citationLabel}>Sumber:</AppText>
            {message.citations.map((c, i) => (
              <AppText key={i} style={styles.citation}>
                [{i + 1}] {c.sectionTitle || c.documentType || c.sourceObjectKey || c.documentId}
              </AppText>
            ))}
          </View>
        )}
        {message.confidence && (
          <AppText style={styles.confidence}>Kepercayaan: {message.confidence}</AppText>
        )}
      </View>
    </View>
  );
}

export default function AssistantScreen() {
  const theme = useTheme();
  const styles = useThemedStyles(makeStyles);
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      content: 'Halo! Saya asisten AI Notarist. Tanyakan apa saja tentang dokumen hukum, akta, sertifikat, atau regulasi notaris Indonesia.',
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const sessionIdRef = useRef(null);
  const flatListRef = useRef(null);

  useEffect(() => {
    (async () => {
      const id = await getOrCreateSessionId();
      sessionIdRef.current = id;
      try {
        const turns = await getConversationHistory(id);
        if (turns && turns.length > 0) {
          setMessages(prev => [...turnsToMessages(turns), ...prev]);
        }
      } catch (_) {
        // no prior history (or backend unreachable) — keep just the welcome message
      }
    })();
  }, []);

  const sendMessage = useCallback(async () => {
    const text = input.trim();
    if (!text || loading || !sessionIdRef.current) return;

    setInput('');
    const userMsg = { id: Date.now().toString(), role: 'user', content: text };
    setMessages(prev => [...prev, userMsg]);

    setLoading(true);
    try {
      const response = await askAssistant(text, sessionIdRef.current);
      const aiMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response.answerText || response.answer || 'Maaf, saya tidak mendapat jawaban.',
        citations: response.citations || [],
        confidence: response.confidence,
      };
      setMessages(prev => [...prev, aiMsg]);
    } catch (err) {
      const errMsg = err.response?.data?.errorMessage || 'Gagal menghubungi asisten AI. Pastikan backend berjalan.';
      const errorMsg = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `⚠️ ${errMsg}`,
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  }, [input, loading]);

  const suggestedQueries = [
    'Apa itu akta notaris?',
    'Syarat fidusia tanah',
    'Prosedur PPAT',
    'Apa itu APHT?',
  ];

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <FlatList
        ref={flatListRef}
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <MessageBubble message={item} styles={styles} />}
        contentContainerStyle={styles.messageList}
        onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
      />

      {messages.length <= 1 && (
        <View style={styles.suggestions}>
          {suggestedQueries.map((q) => (
            <TouchableOpacity
              key={q}
              style={styles.suggestionChip}
              onPress={() => { setInput(q); }}
            >
              <AppText style={styles.suggestionText}>{q}</AppText>
            </TouchableOpacity>
          ))}
        </View>
      )}

      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="Tanyakan sesuatu tentang dokumen notaris..."
          placeholderTextColor={theme.colors.textFaint}
          value={input}
          onChangeText={setInput}
          multiline
          maxLength={1000}
          returnKeyType="send"
          onSubmitEditing={sendMessage}
        />
        <TouchableOpacity
          style={[styles.sendBtn, (!input.trim() || loading) && styles.sendBtnDisabled]}
          onPress={sendMessage}
          disabled={!input.trim() || loading}
        >
          {loading ? (
            <ActivityIndicator color={theme.colors.primaryText} size="small" />
          ) : (
            <AppText style={styles.sendIcon}>↑</AppText>
          )}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const makeStyles = (theme) => ({
  container: { flex: 1, backgroundColor: theme.colors.background },
  messageList: { padding: theme.spacing.lg, paddingBottom: theme.spacing.sm },
  bubbleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: theme.spacing.lg,
  },
  bubbleRowUser: {
    justifyContent: 'flex-end',
  },
  avatar: { fontSize: 20, marginRight: theme.spacing.sm, marginTop: theme.spacing.xs },
  bubble: {
    maxWidth: '80%',
    borderRadius: theme.radius.lg,
    padding: theme.spacing.md,
  },
  aiBubble: {
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  userBubble: {
    backgroundColor: theme.colors.primary,
  },
  bubbleText: {
    color: theme.colors.text,
    fontSize: 14,
    lineHeight: 21,
  },
  userBubbleText: { color: theme.colors.primaryText },
  citations: {
    marginTop: theme.spacing.sm,
    paddingTop: theme.spacing.sm,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
  },
  citationLabel: {
    color: theme.colors.textMuted,
    fontSize: theme.typography.micro,
    fontWeight: theme.typography.semibold,
    marginBottom: 2,
  },
  citation: { color: theme.colors.primary, fontSize: theme.typography.micro },
  confidence: { color: theme.colors.textFaint, fontSize: theme.typography.micro, marginTop: 6 },
  suggestions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: theme.spacing.sm,
    paddingHorizontal: theme.spacing.lg,
    paddingBottom: theme.spacing.sm,
  },
  suggestionChip: {
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.pill,
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 6,
    borderWidth: 1,
    borderColor: theme.colors.primary,
  },
  suggestionText: { color: theme.colors.primary, fontSize: theme.typography.caption },
  inputRow: {
    flexDirection: 'row',
    padding: theme.spacing.md,
    gap: theme.spacing.sm,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    backgroundColor: theme.colors.background,
  },
  input: {
    flex: 1,
    backgroundColor: theme.colors.surface,
    color: theme.colors.text,
    borderRadius: theme.radius.xl,
    paddingHorizontal: theme.spacing.lg,
    paddingVertical: 10,
    fontSize: 14,
    maxHeight: 100,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: theme.colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    alignSelf: 'flex-end',
  },
  sendBtnDisabled: { opacity: 0.4 },
  sendIcon: { color: theme.colors.primaryText, fontSize: 20, fontWeight: theme.typography.bold },
});
