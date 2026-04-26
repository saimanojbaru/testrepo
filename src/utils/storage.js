import AsyncStorage from '@react-native-async-storage/async-storage';

const PENDING_KEY = '@upi_pending_ledger';
const HISTORY_KEY = '@upi_history_ledger';

const safeParse = (raw) => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

export const getPending = async () => {
  const raw = await AsyncStorage.getItem(PENDING_KEY);
  return safeParse(raw);
};

export const setPending = async (items) => {
  await AsyncStorage.setItem(PENDING_KEY, JSON.stringify(items));
};

export const getHistory = async () => {
  const raw = await AsyncStorage.getItem(HISTORY_KEY);
  return safeParse(raw);
};

export const setHistory = async (items) => {
  await AsyncStorage.setItem(HISTORY_KEY, JSON.stringify(items));
};

export const addPending = async (entry) => {
  const list = await getPending();
  const next = [entry, ...list];
  await setPending(next);
  return next;
};

export const movePendingToHistory = async (id) => {
  const pending = await getPending();
  const target = pending.find((p) => p.id === id);
  if (!target) return { pending, history: await getHistory() };
  const remaining = pending.filter((p) => p.id !== id);
  const completed = { ...target, status: 'completed', completedAt: Date.now() };
  const history = await getHistory();
  const nextHistory = [completed, ...history];
  await setPending(remaining);
  await setHistory(nextHistory);
  return { pending: remaining, history: nextHistory };
};

export const clearAll = async () => {
  await AsyncStorage.multiRemove([PENDING_KEY, HISTORY_KEY]);
};
