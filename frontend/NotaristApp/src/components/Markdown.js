import React, { useMemo } from 'react';
import { View, Text } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

// A small, dependency-free Markdown renderer for assistant answers. The LLM emits lightweight
// Markdown (headings, bullet/numbered lists, **bold**, *italic*, `code`) and rendering it as raw text
// leaves asterisks and hashes on screen. A full CommonMark parser is not worth a native dependency
// here — this handles the subset the model actually produces and, above all, NEVER throws: an answer
// that fails to parse must still render as plain text, because it is a legal answer the notary asked
// for. Everything falls through to a paragraph rather than being dropped.
//
// It re-parses on every render, which during streaming means once per token batch. Answers are short
// (a few paragraphs) and parsing is a single linear pass, so this stays cheap; if answers ever grow
// long enough to matter, the parse is already isolated behind useMemo(text).

// --- inline: **bold**, `code`, *italic* / _italic_ ---
const INLINE_RE = /(\*\*([^*]+)\*\*|`([^`]+)`|\*([^*\n]+)\*|_([^_\n]+)_)/g;

function parseInline(text) {
  const tokens = [];
  let last = 0;
  let m;
  INLINE_RE.lastIndex = 0;
  while ((m = INLINE_RE.exec(text))) {
    if (m.index > last) tokens.push({ text: text.slice(last, m.index) });
    if (m[2] !== undefined) tokens.push({ text: m[2], bold: true });
    else if (m[3] !== undefined) tokens.push({ text: m[3], code: true });
    else if (m[4] !== undefined) tokens.push({ text: m[4], italic: true });
    else if (m[5] !== undefined) tokens.push({ text: m[5], italic: true });
    last = INLINE_RE.lastIndex;
  }
  if (last < text.length) tokens.push({ text: text.slice(last) });
  return tokens.length ? tokens : [{ text }];
}

function Inline({ text, theme }) {
  return parseInline(text).map((t, i) => (
    <Text
      key={i}
      style={
        t.code
          ? { fontFamily: mono, backgroundColor: theme.colors.surfaceAlt, color: theme.colors.text }
          : {
              fontWeight: t.bold ? theme.typography.semibold : undefined,
              fontStyle: t.italic ? 'italic' : undefined,
            }
      }
    >
      {t.text}
    </Text>
  ));
}

const mono = 'monospace';

// --- block grouping ---
const HEADING_RE = /^(#{1,3})\s+(.*)$/;
const BULLET_RE = /^\s*[-*•]\s+(.*)$/;
const NUMBERED_RE = /^\s*(\d+)[.)]\s+(.*)$/;

function parseBlocks(md) {
  const lines = String(md).replace(/\r\n/g, '\n').split('\n');
  const blocks = [];
  let para = [];
  let list = null; // { ordered, items: [] }

  const flushPara = () => {
    if (para.length) { blocks.push({ type: 'p', text: para.join('\n') }); para = []; }
  };
  const flushList = () => {
    if (list) { blocks.push({ type: 'list', ...list }); list = null; }
  };

  for (const line of lines) {
    if (!line.trim()) { flushPara(); flushList(); continue; }

    const heading = HEADING_RE.exec(line);
    if (heading) { flushPara(); flushList(); blocks.push({ type: 'h', level: heading[1].length, text: heading[2] }); continue; }

    const numbered = NUMBERED_RE.exec(line);
    if (numbered) {
      flushPara();
      if (!list || !list.ordered) { flushList(); list = { ordered: true, items: [] }; }
      list.items.push(numbered[2]);
      continue;
    }

    const bullet = BULLET_RE.exec(line);
    if (bullet) {
      flushPara();
      if (!list || list.ordered) { flushList(); list = { ordered: false, items: [] }; }
      list.items.push(bullet[1]);
      continue;
    }

    flushList();
    para.push(line);
  }
  flushPara();
  flushList();
  return blocks;
}

const HEADING_VARIANT = { 1: 'h3', 2: 'bodyStrong', 3: 'bodyStrong' };

/**
 * Render lightweight Markdown. `color` is the base text color key (so a user bubble can render its
 * text inverted). Any unrecognised syntax renders as plain text.
 */
export default function Markdown({ text, color = 'text', style }) {
  const theme = useTheme();
  const blocks = useMemo(() => parseBlocks(text ?? ''), [text]);

  return (
    <View style={style}>
      {blocks.map((b, i) => {
        if (b.type === 'h') {
          return (
            <AppText key={i} variant={HEADING_VARIANT[b.level] || 'bodyStrong'} color={color} style={{ marginTop: i ? theme.spacing.sm : 0, marginBottom: 2 }}>
              <Inline text={b.text} theme={theme} />
            </AppText>
          );
        }
        if (b.type === 'list') {
          return (
            <View key={i} style={{ marginTop: i ? theme.spacing.xs : 0 }}>
              {b.items.map((item, j) => (
                <View key={j} style={{ flexDirection: 'row', marginBottom: 2 }}>
                  <AppText color={color} style={{ marginRight: theme.spacing.sm, minWidth: b.ordered ? 18 : 12 }}>
                    {b.ordered ? `${j + 1}.` : '•'}
                  </AppText>
                  <AppText color={color} style={{ flex: 1, lineHeight: 21 }}>
                    <Inline text={item} theme={theme} />
                  </AppText>
                </View>
              ))}
            </View>
          );
        }
        return (
          <AppText key={i} color={color} style={{ marginTop: i ? theme.spacing.sm : 0, lineHeight: 21 }}>
            <Inline text={b.text} theme={theme} />
          </AppText>
        );
      })}
    </View>
  );
}
