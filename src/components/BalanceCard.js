import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { elevation } from '../theme/colors';

const BalanceCard = ({ theme, isOffline, pendingCount, pendingTotal, completedCount }) => {
  const colors = isOffline ? ['#475569', '#1f2937'] : ['#0f172a', '#1e293b'];

  return (
    <LinearGradient
      colors={colors}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[styles.card, elevation.large]}
    >
      <View style={styles.headerRow}>
        <Text style={styles.label}>OFFLINE LEDGER</Text>
        <View style={[styles.statusPill, { backgroundColor: isOffline ? 'rgba(245,158,11,0.25)' : 'rgba(34,197,94,0.25)' }]}>
          <View style={[styles.statusDot, { backgroundColor: isOffline ? '#fbbf24' : '#4ade80' }]} />
          <Text style={styles.statusLabel}>{isOffline ? 'OFFLINE' : 'LIVE'}</Text>
        </View>
      </View>

      <Text style={styles.amountLabel}>Pending value</Text>
      <Text style={styles.amount}>₹{pendingTotal.toFixed(2)}</Text>

      <View style={styles.divider} />

      <View style={styles.statsRow}>
        <View style={styles.stat}>
          <Text style={styles.statValue}>{pendingCount}</Text>
          <Text style={styles.statLabel}>Pending</Text>
        </View>
        <View style={styles.statSeparator} />
        <View style={styles.stat}>
          <Text style={styles.statValue}>{completedCount}</Text>
          <Text style={styles.statLabel}>Completed</Text>
        </View>
      </View>
    </LinearGradient>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 22,
    padding: 22,
    marginBottom: 22,
    overflow: 'hidden',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 18,
  },
  label: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
  },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3, marginRight: 6 },
  statusLabel: { color: '#ffffff', fontSize: 10, fontWeight: '800', letterSpacing: 0.8 },
  amountLabel: { color: 'rgba(255,255,255,0.65)', fontSize: 12, marginBottom: 4, letterSpacing: 0.4 },
  amount: { color: '#ffffff', fontSize: 36, fontWeight: '800', letterSpacing: -0.5 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255,255,255,0.18)', marginVertical: 18 },
  statsRow: { flexDirection: 'row', alignItems: 'center' },
  stat: { flex: 1 },
  statSeparator: { width: StyleSheet.hairlineWidth, alignSelf: 'stretch', backgroundColor: 'rgba(255,255,255,0.18)' },
  statValue: { color: '#ffffff', fontSize: 22, fontWeight: '700' },
  statLabel: { color: 'rgba(255,255,255,0.6)', fontSize: 11, marginTop: 2, letterSpacing: 0.4, textTransform: 'uppercase' },
});

export default BalanceCard;
