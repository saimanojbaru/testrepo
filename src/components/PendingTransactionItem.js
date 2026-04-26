import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

import { formatCurrency, formatTimestamp } from '../utils/format';

const PendingTransactionItem = ({ item, theme, isSyncing, isCompleted }) => {
  const badgeBg = isCompleted ? theme.completedBg : theme.pendingBg;
  const badgeColor = isCompleted ? theme.completedText : theme.pendingText;
  const label = isCompleted ? 'COMPLETED' : isSyncing ? 'SYNCING' : 'PENDING';

  return (
    <View style={[styles.row, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <View style={styles.left}>
        <Text style={[styles.recipient, { color: theme.text }]} numberOfLines={1}>
          {item.recipient}
        </Text>
        <Text style={[styles.timestamp, { color: theme.subtext }]} numberOfLines={1}>
          {formatTimestamp(item.timestamp)}
        </Text>
      </View>
      <View style={styles.right}>
        <Text style={[styles.amount, { color: theme.text }]}>{formatCurrency(item.amount)}</Text>
        <View style={[styles.badge, { backgroundColor: badgeBg }]}>
          {isSyncing ? <ActivityIndicator size="small" color={badgeColor} style={styles.badgeSpinner} /> : null}
          <Text style={[styles.badgeText, { color: badgeColor }]}>{label}</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 10,
  },
  left: { flex: 1, paddingRight: 8 },
  right: { alignItems: 'flex-end' },
  recipient: { fontSize: 15, fontWeight: '600' },
  timestamp: { fontSize: 12, marginTop: 2 },
  amount: { fontSize: 16, fontWeight: '700' },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
    marginTop: 4,
  },
  badgeText: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
  badgeSpinner: { marginRight: 4, transform: [{ scale: 0.7 }] },
});

export default PendingTransactionItem;
