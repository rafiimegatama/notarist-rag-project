// Assistant conversation summary + date grouping for the Conversation History screen.
//
// Sprint 5 (Task 2): hardened. There is still no list-all endpoint (only GET
// /assistant/history/{sessionId}), so the wire shape of a conversation SUMMARY is not yet fixed —
// which is exactly why this reads through aliases rather than one spelling. `lastMessageAt` and
// `messageCount` are accepted because a history-derived summary is the likeliest source.
import { pick, str, num, isoDate, withExtras, makeNormalizer } from './normalize';

const CONSUMED = [
  'id', 'sessionId', 'title', 'lastMessage', 'lastMessageAt', 'updatedAt', 'createdAt',
  'messageCount', 'preview',
];

export function normalizeConversation(raw = {}) {
  const out = {
    id: str(pick(raw, ['id', 'sessionId']), null),
    sessionId: str(pick(raw, ['sessionId', 'id']), null),
    // 'Percakapan' was a hardcoded default that made every untitled conversation look identically
    // named. null lets the screen decide; it already renders with numberOfLines={1} safely.
    title: str(pick(raw, ['title']), null),
    lastMessage: str(pick(raw, ['lastMessage', 'preview']), null),
    messageCount: num(pick(raw, ['messageCount']), null),
    updatedAt: isoDate(pick(raw, ['updatedAt', 'lastMessageAt']), null)
      ?? isoDate(pick(raw, ['createdAt']), null),
  };
  return withExtras(out, raw, CONSUMED);
}

export const ConversationNormalizer = makeNormalizer(normalizeConversation);

// Group key used by the section list: Today / Yesterday / This Week / This Month / Older.
export function groupKey(updatedAt, now = Date.now()) {
  if (!updatedAt) return 'older';
  const startOfToday = new Date(now); startOfToday.setHours(0, 0, 0, 0);
  const t = Date.parse(updatedAt);
  const dayMs = 86400000;
  if (t >= startOfToday.getTime()) return 'today';
  if (t >= startOfToday.getTime() - dayMs) return 'yesterday';
  if (t >= startOfToday.getTime() - 7 * dayMs) return 'thisWeek';
  if (t >= startOfToday.getTime() - 30 * dayMs) return 'thisMonth';
  return 'older';
}

export const GROUP_ORDER = ['today', 'yesterday', 'thisWeek', 'thisMonth', 'older'];
export const GROUP_LABELS = {
  today: 'Hari Ini',
  yesterday: 'Kemarin',
  thisWeek: 'Minggu Ini',
  thisMonth: 'Bulan Ini',
  older: 'Lebih Lama',
};

export function groupConversations(items, now = Date.now()) {
  const buckets = Object.fromEntries(GROUP_ORDER.map((k) => [k, []]));
  for (const c of items) buckets[groupKey(c.updatedAt, now)].push(c);
  return GROUP_ORDER
    .filter((k) => buckets[k].length)
    .map((k) => ({ key: k, title: GROUP_LABELS[k], data: buckets[k] }));
}
