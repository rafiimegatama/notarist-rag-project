import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  Alert,
} from 'react-native';
import * as Crypto from 'expo-crypto';
import { askAssistant } from '../api/assistant';

let sessionId = null;
function getSessionId() {
  if (!sessionId) {
    sessionId = Crypto.randomUUID();
  }
  return sessionId;
}

function MessageBubble({ message }) {
  const isUser = message.role === 'user';
  return (
    <View style={[styles.bubbleRow, isUser && styles.bubbleRowUser]}>
      {!isUser && <Text style={styles.avatar}>🤖</Text>}
      <View style={[styles.bubble, isUser ? styles.userBubble : styles.aiBubble]}>
        <Text style={[styles.bubbleText, isUser && styles.userBubbleText]}>
          {message.content}
        </Text>
        {message.citations && message.citations.length > 0 && (
          <View style={styles.citations}>
            <Text style={styles.citationLabel}>Sumber:</Text>
            {message.citations.map((c, i) => (
              <Text key={i} style={styles.citation}>
                [{i + 1}] {c.sectionTitle || c.documentType || c.sourceObjectKey || c.documentId}
              </Text>
            ))}
          </View>
        )}
        {message.confidence && (
          <Text style={styles.confidence}>Kepercayaan: {message.confidence}</Text>
        )}
      </View>
    </View>
  );
}

export default function AssistantScreen() {
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      content: 'Halo! Saya asisten AI Notarist. Tanyakan apa saja tentang dokumen hukum, akta, sertifikat, atau regulasi notaris Indonesia.',
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const flatListRef = useRef(null);

  const sendMessage = useCallback(async () => {
    const text = input.trim();
    if (!text || loading) return;

    setInput('');
    const userMsg = { id: Date.now().toString(), role: 'user', content: text };
    setMessages(prev => [...prev, userMsg]);

    setLoading(true);
    try {
      const response = await askAssistant(text, getSessionId());
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
        renderItem={({ item }) => <MessageBubble message={item} />}
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
              <Text style={styles.suggestionText}>{q}</Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="Tanyakan sesuatu tentang dokumen notaris..."
          placeholderTextColor="#475569"
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
            <ActivityIndicator color="#fff" size="small" />
          ) : (
            <Text style={styles.sendIcon}>↑</Text>
          )}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0F172A' },
  messageList: { padding: 16, paddingBottom: 8 },
  bubbleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  bubbleRowUser: {
    justifyContent: 'flex-end',
  },
  avatar: { fontSize: 20, marginRight: 8, marginTop: 4 },
  bubble: {
    maxWidth: '80%',
    borderRadius: 12,
    padding: 12,
  },
  aiBubble: {
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: '#334155',
  },
  userBubble: {
    backgroundColor: '#2563EB',
  },
  bubbleText: {
    color: '#CBD5E1',
    fontSize: 14,
    lineHeight: 21,
  },
  userBubbleText: { color: '#fff' },
  citations: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#334155',
  },
  citationLabel: { color: '#94A3B8', fontSize: 11, fontWeight: '600', marginBottom: 2 },
  citation: { color: '#60A5FA', fontSize: 11 },
  confidence: { color: '#64748B', fontSize: 11, marginTop: 6 },
  suggestions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  suggestionChip: {
    backgroundColor: '#1E293B',
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderWidth: 1,
    borderColor: '#3B82F6',
  },
  suggestionText: { color: '#60A5FA', fontSize: 12 },
  inputRow: {
    flexDirection: 'row',
    padding: 12,
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: '#1E293B',
    backgroundColor: '#0F172A',
  },
  input: {
    flex: 1,
    backgroundColor: '#1E293B',
    color: '#F1F5F9',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 14,
    maxHeight: 100,
    borderWidth: 1,
    borderColor: '#334155',
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#3B82F6',
    justifyContent: 'center',
    alignItems: 'center',
    alignSelf: 'flex-end',
  },
  sendBtnDisabled: { opacity: 0.4 },
  sendIcon: { color: '#fff', fontSize: 20, fontWeight: '700' },
});
