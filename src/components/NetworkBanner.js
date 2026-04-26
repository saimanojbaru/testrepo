import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

import { useNetwork } from '../context/NetworkContext';

const NetworkBanner = () => {
  const { isOffline, isSyncing, theme, pending } = useNetwork();

  const message = isOffline
    ? 'You are offline. Payments will be queued.'
    : isSyncing
      ? `Online. Syncing ${pending.length} pending payment${pending.length === 1 ? '' : 's'}...`
      : 'Online. Syncing pending payments...';

  return (
    <View style={[styles.container, { backgroundColor: theme.bannerBg }]}>
      {!isOffline && isSyncing ? (
        <ActivityIndicator size="small" color={theme.bannerText} style={styles.spinner} />
      ) : (
        <View style={[styles.dot, { backgroundColor: isOffline ? '#fff' : '#22c55e' }]} />
      )}
      <Text style={[styles.text, { color: theme.bannerText }]} numberOfLines={1}>
        {message}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    paddingVertical: 10,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  text: {
    fontSize: 14,
    fontWeight: '600',
    flexShrink: 1,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: 10,
  },
  spinner: {
    marginRight: 10,
  },
});

export default NetworkBanner;
