import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Animated, Easing } from 'react-native';

import { formatCurrency, formatTimestamp } from '../utils/format';
import { elevation } from '../theme/colors';

const Avatar = ({ recipient, theme }) => {
  const initial = (recipient || '?').trim().charAt(0).toUpperCase();
  return (
    <View style={[styles.avatar, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
      <Text style={[styles.avatarText, { color: theme.text }]}>{initial}</Text>
    </View>
  );
};

const StatusBadge = ({ theme, isSyncing, isCompleted }) => {
  const config = isCompleted
    ? { bg: theme.completedBg, color: theme.completedText, dot: theme.completedDot, label: 'COMPLETED' }
    : isSyncing
      ? { bg: theme.syncingBg, color: theme.syncingText, dot: theme.accent, label: 'SYNCING' }
      : { bg: theme.pendingBg, color: theme.pendingText, dot: theme.pendingDot, label: 'PENDING' };

  return (
    <View style={[styles.badge, { backgroundColor: config.bg }]}>
      {isSyncing ? (
        <ActivityIndicator size="small" color={config.color} style={styles.badgeSpinner} />
      ) : (
        <View style={[styles.badgeDot, { backgroundColor: config.dot }]} />
      )}
      <Text style={[styles.badgeText, { color: config.color }]}>{config.label}</Text>
    </View>
  );
};

const PendingTransactionItem = ({ item, theme, isSyncing, isCompleted, index = 0 }) => {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(8)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 280, delay: index * 40, useNativeDriver: true }),
      Animated.timing(translateY, { toValue: 0, duration: 320, delay: index * 40, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
    ]).start();
  }, [opacity, translateY, index]);

  return (
    <Animated.View
      style={[
        styles.row,
        {
          backgroundColor: theme.surface,
          borderColor: theme.border,
          opacity,
          transform: [{ translateY }],
        },
        elevation.small,
      ]}
    >
      <Avatar recipient={item.recipient} theme={theme} />
      <View style={styles.middle}>
        <Text style={[styles.recipient, { color: theme.text }]} numberOfLines={1}>
          {item.recipient}
        </Text>
        <Text style={[styles.timestamp, { color: theme.subtext }]} numberOfLines={1}>
          {formatTimestamp(item.timestamp)}
        </Text>
        {item.note ? (
          <Text style={[styles.note, { color: theme.muted }]} numberOfLines={1}>
            {item.note}
          </Text>
        ) : null}
      </View>
      <View style={styles.right}>
        <Text style={[styles.amount, { color: theme.text }]}>{formatCurrency(item.amount)}</Text>
        <StatusBadge theme={theme} isSyncing={isSyncing} isCompleted={isCompleted} />
      </View>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    paddingHorizontal: 14,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    marginBottom: 12,
  },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  avatarText: { fontSize: 16, fontWeight: '700' },
  middle: { flex: 1, paddingRight: 8 },
  right: { alignItems: 'flex-end' },
  recipient: { fontSize: 15, fontWeight: '600', letterSpacing: 0.1 },
  timestamp: { fontSize: 11, marginTop: 2 },
  note: { fontSize: 11, marginTop: 2, fontStyle: 'italic' },
  amount: { fontSize: 16, fontWeight: '700', letterSpacing: 0.2 },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
    marginTop: 6,
  },
  badgeText: { fontSize: 9.5, fontWeight: '800', letterSpacing: 0.7 },
  badgeSpinner: { marginRight: 5, transform: [{ scale: 0.6 }] },
  badgeDot: { width: 6, height: 6, borderRadius: 3, marginRight: 6 },
});

export default PendingTransactionItem;
