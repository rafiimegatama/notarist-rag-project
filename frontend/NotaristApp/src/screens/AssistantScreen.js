import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View,
  TextInput,
  FlatList,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  LayoutAnimation,
  UIManager,
} from 'react-native';
import { getToken, setToken } from '../utils/tokenStore';
import * as Crypto from 'expo-crypto';
import { askAssistant, getConversationHistory } from '../api/assistant';
import { streamAssistant } from '../api/assistantStream';
import { FEATURES } from '../constants/config';
import { messageFor } from '../api/errors';
import { useTheme } from '../context/ThemeContext';
import useThemedStyles from '../hooks/useThemedStyles';
import AppText from '../components/AppText';
import Markdown from '../components/Markdown';
import GroundingBadge from '../components/GroundingBadge';
import TypingIndicator from '../components/TypingIndicator';
import { copyToClipboard } from '../utils/clipboard';
import { announce } from '../utils/a11y';

const SESSION_STORE_KEY = 'assistant_session_id';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

// Session id must be persisted, not regenerated per mount — a fresh id every
// time means getConversationHistory() can never find a matching prior session.
async function getOrCreateSessionId() {
  let id = await getToken(SESSION_STORE_KEY);
  if (!id) {
    id = Crypto.randomUUID();
    await setToken(SESSION_STORE_KEY, id);
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

const WELCOME = {
  id: 'welcome',
  role: 'assistant',
  content:
    'Halo! Saya asisten AI Notarist. Tanyakan apa saja tentang dokumen hukum, akta, sertifikat, atau regulasi notaris Indonesia.',
};

// Streamed CitationDto -> the fields the viewer renders. Title falls back through the identifying
// fields the DTO actually carries; an absent chunkText is said plainly, never rendered as an empty
// source. (Same honesty rule as components/CitationCard.)
function citationTitle(c) {
  return c.sectionTitle || c.documentType || c.sourceObjectKey || c.documentId || 'Sumber';
}

function CitationItem({ citation, index, styles, theme }) {
  const [open, setOpen] = useState(false);
  const score = typeof citation.relevanceScore === 'number' && Number.isFinite(citation.relevanceScore)
    ? citation.relevanceScore
    : null;
  const toggle = () => {
    LayoutAnimation.configureNext(
      LayoutAnimation.create(theme.durations.fast, LayoutAnimation.Types.easeInEaseOut, LayoutAnimation.Properties.opacity),
    );
    setOpen((o) => !o);
  };
  return (
    <TouchableOpacity activeOpacity={0.85} onPress={toggle} accessibilityRole="button" accessibilityLabel={`Sumber ${index + 1}, ${open ? 'tutup' : 'buka'}`}>
      <View style={styles.citationItem}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <AppText variant="micro" color="primary" style={{ flex: 1 }} numberOfLines={open ? undefined : 1}>
            [{index + 1}] {citationTitle(citation)}
          </AppText>
          {score !== null && (
            <AppText variant="micro" color="textFaint" style={{ marginLeft: theme.spacing.sm }}>
              {score.toFixed(2)}
            </AppText>
          )}
        </View>
        {open && (
          <AppText variant="micro" color="textMuted" style={{ marginTop: 4, lineHeight: 16 }}>
            {citation.chunkText || 'Teks kutipan tidak tersedia.'}
          </AppText>
        )}
      </View>
    </TouchableOpacity>
  );
}

function MessageBubble({ message, styles, theme, onCopy, copied, onRegenerate, onStop }) {
  const isUser = message.role === 'user';
  const hasContent = !!(message.content && message.content.length);
  const showTyping = message.streaming && !hasContent;
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const citations = message.citations || [];
  const warnings = message.warnings || [];

  return (
    <View style={[styles.bubbleRow, isUser && styles.bubbleRowUser]}>
      {!isUser && <AppText style={styles.avatar}>🤖</AppText>}
      <View style={[styles.bubble, isUser ? styles.userBubble : styles.aiBubble]}>
        {showTyping ? (
          <TypingIndicator />
        ) : isUser ? (
          <AppText style={[styles.bubbleText, styles.userBubbleText]}>{message.content}</AppText>
        ) : (
          <Markdown text={message.content} color="text" />
        )}

        {/* Grounding + confidence badge (assistant only). */}
        {!isUser && message.confidence?.level && (
          <GroundingBadge level={message.confidence.level} score={message.confidence.score} style={{ marginTop: theme.spacing.sm }} />
        )}

        {/* Hallucination / low-grounding warnings. */}
        {!isUser && warnings.length > 0 && (
          <View style={styles.warningBox}>
            {warnings.map((w, i) => (
              <AppText key={i} variant="micro" color="warning" style={{ lineHeight: 16 }}>
                ⚠️ {w}
              </AppText>
            ))}
          </View>
        )}

        {/* Citations — collapsed by default, opens the source panel. */}
        {!isUser && citations.length > 0 && (
          <View style={styles.citations}>
            <TouchableOpacity onPress={() => setSourcesOpen((o) => !o)} accessibilityRole="button">
              <AppText variant="micro" color="textMuted" style={{ fontWeight: theme.typography.semibold }}>
                {sourcesOpen ? '▲' : '▼'} Sumber ({citations.length})
              </AppText>
            </TouchableOpacity>
            {sourcesOpen &&
              citations.map((c, i) => (
                <CitationItem key={c.chunkId || i} citation={c} index={i} styles={styles} theme={theme} />
              ))}
          </View>
        )}

        {/* Per-message actions (assistant, non-welcome). */}
        {!isUser && message.id !== 'welcome' && (
          <View style={styles.msgActions}>
            {message.streaming ? (
              <TouchableOpacity onPress={onStop} accessibilityRole="button" accessibilityLabel="Hentikan pembuatan jawaban">
                <AppText variant="micro" color="danger">⏹ Hentikan</AppText>
              </TouchableOpacity>
            ) : (
              <>
                {hasContent && (
                  <TouchableOpacity onPress={() => onCopy(message)} accessibilityRole="button" accessibilityLabel="Salin jawaban">
                    <AppText variant="micro" color={copied ? 'success' : 'textMuted'}>
                      {copied ? '✓ Disalin' : '⧉ Salin'}
                    </AppText>
                  </TouchableOpacity>
                )}
                {message.canRegenerate && (
                  <TouchableOpacity onPress={() => onRegenerate(message)} accessibilityRole="button" accessibilityLabel="Buat ulang jawaban">
                    <AppText variant="micro" color="primary">↻ Buat ulang</AppText>
                  </TouchableOpacity>
                )}
              </>
            )}
            {message.stopped && (
              <AppText variant="micro" color="textFaint">dihentikan</AppText>
            )}
          </View>
        )}
      </View>
    </View>
  );
}

export default function AssistantScreen({ route }) {
  const theme = useTheme();
  const styles = useThemedStyles(makeStyles);
  const [messages, setMessages] = useState([WELCOME]);
  const [input, setInput] = useState('');
  const [generating, setGenerating] = useState(false);
  const [copiedId, setCopiedId] = useState(null);
  const [followUps, setFollowUps] = useState([]);
  const sessionIdRef = useRef(null);
  const streamRef = useRef(null);       // active stream handle, for Stop
  const flatListRef = useRef(null);
  const copiedTimer = useRef(null);

  const routeSessionId = route?.params?.sessionId;

  // Load session + prior history. Re-runs when a conversation is opened from the history screen with a
  // specific sessionId (closing the Sprint-3 debt noted in ConversationsScreen).
  useEffect(() => {
    let alive = true;
    (async () => {
      const id = routeSessionId || (await getOrCreateSessionId());
      if (!alive) return;
      sessionIdRef.current = id;
      setMessages([WELCOME]);
      try {
        const turns = await getConversationHistory(id);
        if (alive && turns && turns.length > 0) {
          setMessages((prev) => [...turnsToMessages(turns), ...prev]);
        }
      } catch (_) {
        // no prior history (or backend unreachable) — keep just the welcome message
      }
    })();
    return () => { alive = false; };
  }, [routeSessionId]);

  // Cancel any in-flight stream on unmount so a backgrounded screen does not leak an LLM inference.
  useEffect(() => () => {
    streamRef.current?.abort();
    if (copiedTimer.current) clearTimeout(copiedTimer.current);
  }, []);

  // Update one message in place by id.
  const patchMessage = useCallback((id, patch) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, ...(typeof patch === 'function' ? patch(m) : patch) } : m)));
  }, []);

  // Non-streaming path: one request, whole answer. Used when the stream flag is off, when the stream
  // fails before a single token (so the notary still gets a real answer), or as the platform fallback.
  const runBlocking = useCallback(async (text, aid) => {
    try {
      const response = await askAssistant(text, sessionIdRef.current);
      patchMessage(aid, {
        content: response.answerText || response.answer || 'Maaf, saya tidak mendapat jawaban.',
        citations: response.citations || [],
        confidence: response.confidence ? { level: response.confidence, score: response.groundingScore ?? null } : null,
        warnings: response.warnings || [],
        streaming: false,
        canRegenerate: true,
      });
      setFollowUps(response.followUpQuestions || []);
    } catch (err) {
      patchMessage(aid, { content: `⚠️ ${messageFor(err)}`, streaming: false, error: true, canRegenerate: true });
    } finally {
      setGenerating(false);
      streamRef.current = null;
    }
  }, [patchMessage]);

  const send = useCallback((rawText) => {
    const text = (rawText ?? input).trim();
    if (!text || generating || !sessionIdRef.current) return;

    setInput('');
    setFollowUps([]);
    const uid = `u${Date.now()}`;
    const aid = `a${Date.now()}`;
    setMessages((prev) => [
      ...prev,
      { id: uid, role: 'user', content: text },
      { id: aid, role: 'assistant', content: '', streaming: true, citations: [], warnings: [], confidence: null },
    ]);
    setGenerating(true);

    if (!FEATURES.assistantStreamEndpoint) {
      runBlocking(text, aid);
      return;
    }

    let gotToken = false;
    try {
      streamRef.current = streamAssistant(text, sessionIdRef.current, {}, {
        onToken: (token) => {
          gotToken = true;
          patchMessage(aid, (m) => ({ content: m.content + token }));
        },
        onCitation: (citation) => patchMessage(aid, (m) => ({ citations: [...(m.citations || []), citation] })),
        onConfidence: ({ level, score }) => patchMessage(aid, { confidence: { level, score } }),
        onWarning: (warning) => patchMessage(aid, (m) => ({ warnings: [...(m.warnings || []), warning] })),
        onFollowUp: (question) => setFollowUps((prev) => (prev.includes(question) ? prev : [...prev, question])),
        onDone: () => {
          patchMessage(aid, { streaming: false, canRegenerate: true });
          setGenerating(false);
          streamRef.current = null;
        },
        onError: (err) => {
          streamRef.current = null;
          // Nothing streamed yet -> fall back to the whole-answer endpoint so the notary still gets a
          // real answer rather than an error for a transient stream hiccup. Tokens already on screen
          // -> keep them and surface the failure; re-asking would duplicate the answer.
          if (!gotToken) {
            runBlocking(text, aid);
          } else {
            patchMessage(aid, (m) => ({ content: `${m.content}\n\n⚠️ ${messageFor(err)}`, streaming: false, error: true, canRegenerate: true }));
            setGenerating(false);
          }
        },
      });
    } catch (err) {
      // requireEndpoint / synchronous setup failure — degrade to the blocking path.
      streamRef.current = null;
      runBlocking(text, aid);
    }
  }, [input, generating, patchMessage, runBlocking]);

  const stop = useCallback(() => {
    streamRef.current?.abort();
    streamRef.current = null;
    setGenerating(false);
    setMessages((prev) => prev.map((m) => (m.streaming ? { ...m, streaming: false, stopped: true, canRegenerate: true } : m)));
  }, []);

  // Regenerate: re-ask the question that produced this answer. Finds the immediately preceding user
  // turn so the button works on any answer, not just the last.
  const regenerate = useCallback((message) => {
    setMessages((prev) => {
      const idx = prev.findIndex((m) => m.id === message.id);
      for (let i = idx - 1; i >= 0; i -= 1) {
        if (prev[i].role === 'user') { setTimeout(() => send(prev[i].content), 0); break; }
      }
      return prev;
    });
  }, [send]);

  const copy = useCallback(async (message) => {
    const ok = await copyToClipboard(message.content);
    if (!ok) return;
    announce('Jawaban disalin');
    setCopiedId(message.id);
    if (copiedTimer.current) clearTimeout(copiedTimer.current);
    copiedTimer.current = setTimeout(() => setCopiedId(null), 1800);
  }, []);

  const suggestedQueries = [
    'Apa itu akta notaris?',
    'Syarat fidusia tanah',
    'Prosedur PPAT',
    'Apa itu APHT?',
  ];
  // Follow-ups from the answer take over the chip row once a conversation is under way; the seed
  // suggestions only show on a fresh conversation.
  const chips = followUps.length ? followUps : messages.length <= 1 ? suggestedQueries : [];

  const renderItem = useCallback(
    ({ item }) => (
      <MessageBubble
        message={item}
        styles={styles}
        theme={theme}
        onCopy={copy}
        copied={copiedId === item.id}
        onRegenerate={regenerate}
        onStop={stop}
      />
    ),
    [styles, theme, copy, copiedId, regenerate, stop],
  );

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <FlatList
        ref={flatListRef}
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.messageList}
        onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
      />

      {chips.length > 0 && (
        <View style={styles.suggestions}>
          {chips.map((q) => (
            <TouchableOpacity
              key={q}
              style={styles.suggestionChip}
              onPress={() => (followUps.length ? send(q) : setInput(q))}
              accessibilityRole="button"
            >
              <AppText style={styles.suggestionText} numberOfLines={2}>{q}</AppText>
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
          onSubmitEditing={() => send()}
          editable={!generating}
        />
        {generating ? (
          <TouchableOpacity style={[styles.sendBtn, styles.stopBtn]} onPress={stop} accessibilityRole="button" accessibilityLabel="Hentikan">
            <AppText style={styles.stopIcon}>■</AppText>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[styles.sendBtn, !input.trim() && styles.sendBtnDisabled]}
            onPress={() => send()}
            disabled={!input.trim()}
            accessibilityRole="button"
            accessibilityLabel="Kirim"
          >
            <AppText style={styles.sendIcon}>↑</AppText>
          </TouchableOpacity>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const makeStyles = (theme) => ({
  container: { flex: 1, backgroundColor: theme.colors.background },
  messageList: { padding: theme.spacing.lg, paddingBottom: theme.spacing.sm },
  bubbleRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: theme.spacing.lg },
  bubbleRowUser: { justifyContent: 'flex-end' },
  avatar: { fontSize: 20, marginRight: theme.spacing.sm, marginTop: theme.spacing.xs },
  bubble: { maxWidth: '82%', borderRadius: theme.radius.lg, padding: theme.spacing.md },
  aiBubble: { backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border },
  userBubble: { backgroundColor: theme.colors.primary },
  bubbleText: { color: theme.colors.text, fontSize: 14, lineHeight: 21 },
  userBubbleText: { color: theme.colors.primaryText },
  warningBox: {
    marginTop: theme.spacing.sm,
    padding: theme.spacing.sm,
    borderRadius: theme.radius.sm,
    backgroundColor: theme.colors.surfaceAlt,
    borderLeftWidth: 3,
    borderLeftColor: theme.colors.warning,
  },
  citations: {
    marginTop: theme.spacing.sm,
    paddingTop: theme.spacing.sm,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
  },
  citationItem: {
    marginTop: theme.spacing.xs,
    padding: theme.spacing.sm,
    borderRadius: theme.radius.sm,
    backgroundColor: theme.colors.surfaceAlt,
  },
  msgActions: {
    flexDirection: 'row',
    gap: theme.spacing.lg,
    marginTop: theme.spacing.sm,
    paddingTop: theme.spacing.sm,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    alignItems: 'center',
  },
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
    maxWidth: '100%',
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
  stopBtn: { backgroundColor: theme.colors.danger },
  sendIcon: { color: theme.colors.primaryText, fontSize: 20, fontWeight: theme.typography.bold },
  stopIcon: { color: '#fff', fontSize: 16, fontWeight: theme.typography.bold },
});
