import React, { createContext, useContext, useEffect, useMemo, useRef, useState, useCallback } from 'react';
import NetInfo from '@react-native-community/netinfo';

import { getPending, getHistory, setPending, setHistory, addPending, movePendingToHistory } from '../utils/storage';
import { getTheme } from '../theme/colors';

const NetworkContext = createContext(null);

const SYNC_DELAY_MS = 2000;

export const NetworkProvider = ({ children }) => {
  const [isOffline, setIsOffline] = useState(false);
  const [pending, setPendingState] = useState([]);
  const [history, setHistoryState] = useState([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncingId, setSyncingId] = useState(null);
  const wasOfflineRef = useRef(false);
  const syncInProgressRef = useRef(false);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const [p, h] = await Promise.all([getPending(), getHistory()]);
      if (!mounted) return;
      setPendingState(p);
      setHistoryState(h);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const runSync = useCallback(async () => {
    if (syncInProgressRef.current) return;
    syncInProgressRef.current = true;
    setIsSyncing(true);
    try {
      let queue = await getPending();
      while (queue.length > 0) {
        const next = queue[queue.length - 1];
        setSyncingId(next.id);
        await new Promise((resolve) => setTimeout(resolve, SYNC_DELAY_MS));
        const result = await movePendingToHistory(next.id);
        queue = result.pending;
        setPendingState(result.pending);
        setHistoryState(result.history);
      }
    } finally {
      setSyncingId(null);
      setIsSyncing(false);
      syncInProgressRef.current = false;
    }
  }, []);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      const offline = !(state.isConnected && state.isInternetReachable !== false);
      setIsOffline(offline);
      if (wasOfflineRef.current && !offline) {
        runSync();
      }
      wasOfflineRef.current = offline;
    });
    NetInfo.fetch().then((state) => {
      const offline = !(state.isConnected && state.isInternetReachable !== false);
      setIsOffline(offline);
      wasOfflineRef.current = offline;
    });
    return () => unsubscribe();
  }, [runSync]);

  const queuePayment = useCallback(async (entry) => {
    const next = await addPending(entry);
    setPendingState(next);
    return entry;
  }, []);

  const clearAllLocal = useCallback(async () => {
    await setPending([]);
    await setHistory([]);
    setPendingState([]);
    setHistoryState([]);
  }, []);

  const triggerManualSync = useCallback(async () => {
    if (!isOffline) {
      await runSync();
    }
  }, [isOffline, runSync]);

  const theme = useMemo(() => getTheme(isOffline), [isOffline]);

  const value = useMemo(
    () => ({
      isOffline,
      pending,
      history,
      isSyncing,
      syncingId,
      theme,
      queuePayment,
      clearAllLocal,
      triggerManualSync,
    }),
    [isOffline, pending, history, isSyncing, syncingId, theme, queuePayment, clearAllLocal, triggerManualSync],
  );

  return <NetworkContext.Provider value={value}>{children}</NetworkContext.Provider>;
};

export const useNetwork = () => {
  const ctx = useContext(NetworkContext);
  if (!ctx) {
    throw new Error('useNetwork must be used inside NetworkProvider');
  }
  return ctx;
};
