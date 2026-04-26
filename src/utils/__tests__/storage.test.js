jest.mock('@react-native-async-storage/async-storage', () => {
  const store = new Map();
  return {
    __esModule: true,
    default: {
      getItem: jest.fn(async (key) => (store.has(key) ? store.get(key) : null)),
      setItem: jest.fn(async (key, value) => {
        store.set(key, String(value));
      }),
      removeItem: jest.fn(async (key) => {
        store.delete(key);
      }),
      multiRemove: jest.fn(async (keys) => {
        keys.forEach((key) => store.delete(key));
      }),
      clear: jest.fn(async () => {
        store.clear();
      }),
      __store: store,
    },
  };
});

const AsyncStorage = require('@react-native-async-storage/async-storage').default;
const {
  getPending,
  setPending,
  getHistory,
  setHistory,
  addPending,
  movePendingToHistory,
  clearAll,
} = require('../storage');

beforeEach(async () => {
  AsyncStorage.__store.clear();
});

const sampleEntry = (id, recipient = 'alice@ybl', amount = 100) => ({
  id,
  recipient,
  amount,
  timestamp: 1700000000000,
  status: 'pending',
});

describe('storage', () => {
  test('returns empty arrays when nothing is persisted', async () => {
    expect(await getPending()).toEqual([]);
    expect(await getHistory()).toEqual([]);
  });

  test('setPending / getPending round-trip', async () => {
    const items = [sampleEntry('a'), sampleEntry('b')];
    await setPending(items);
    expect(await getPending()).toEqual(items);
  });

  test('setHistory / getHistory round-trip', async () => {
    const items = [sampleEntry('h1')];
    await setHistory(items);
    expect(await getHistory()).toEqual(items);
  });

  test('addPending prepends to the list (newest first)', async () => {
    const first = sampleEntry('first');
    const second = sampleEntry('second');
    await addPending(first);
    const next = await addPending(second);
    expect(next[0].id).toBe('second');
    expect(next[1].id).toBe('first');
    expect(await getPending()).toEqual([second, first]);
  });

  test('movePendingToHistory removes from pending, prepends to history with completed status', async () => {
    await setPending([sampleEntry('a'), sampleEntry('b'), sampleEntry('c')]);
    const result = await movePendingToHistory('b');

    expect(result.pending.map((p) => p.id)).toEqual(['a', 'c']);
    expect(result.history).toHaveLength(1);
    expect(result.history[0]).toMatchObject({
      id: 'b',
      status: 'completed',
    });
    expect(typeof result.history[0].completedAt).toBe('number');
  });

  test('movePendingToHistory is a no-op when id is not present', async () => {
    await setPending([sampleEntry('a')]);
    const result = await movePendingToHistory('missing');
    expect(result.pending.map((p) => p.id)).toEqual(['a']);
    expect(result.history).toEqual([]);
  });

  test('movePendingToHistory keeps history items in newest-first order across multiple calls', async () => {
    await setPending([sampleEntry('a'), sampleEntry('b')]);
    await movePendingToHistory('a');
    const second = await movePendingToHistory('b');
    expect(second.history.map((h) => h.id)).toEqual(['b', 'a']);
    expect(second.pending).toEqual([]);
  });

  test('clearAll empties both ledgers', async () => {
    await setPending([sampleEntry('a')]);
    await setHistory([sampleEntry('h')]);
    await clearAll();
    expect(await getPending()).toEqual([]);
    expect(await getHistory()).toEqual([]);
  });

  test('getPending returns [] if stored value is corrupted JSON', async () => {
    await AsyncStorage.setItem('@upi_pending_ledger', 'not-json{');
    expect(await getPending()).toEqual([]);
  });

  test('getPending returns [] if stored value is JSON but not an array', async () => {
    await AsyncStorage.setItem('@upi_pending_ledger', JSON.stringify({ foo: 'bar' }));
    expect(await getPending()).toEqual([]);
  });
});
