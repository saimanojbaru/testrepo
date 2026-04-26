import React, { useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import PendingTransactionItem from '../components/PendingTransactionItem';
import GradientButton from '../components/GradientButton';
import BalanceCard from '../components/BalanceCard';
import SectionHeader from '../components/SectionHeader';

const EmptyState = ({ theme, message }) => (
  <View style={[styles.emptyState, { backgroundColor: theme.surface, borderColor: theme.border }]}>
    <Text style={[styles.emptyText, { color: theme.subtext }]}>{message}</Text>
  </View>
);

const DashboardScreen = ({ navigation }) => {
  const { theme, pending, history, isOffline, syncingId, triggerManualSync, isSyncing } = useNetwork();

  const pendingTotal = useMemo(
    () => pending.reduce((sum, p) => sum + (Number(p.amount) || 0), 0),
    [pending],
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <Text style={[styles.greeting, { color: theme.subtext }]}>Welcome back</Text>
          <Text style={[styles.heading, { color: theme.text }]}>Offline UPI Capture</Text>
        </View>

        <BalanceCard
          theme={theme}
          isOffline={isOffline}
          pendingCount={pending.length}
          pendingTotal={pendingTotal}
          completedCount={history.length}
        />

        <View style={styles.actionRow}>
          <GradientButton
            colors={theme.accentGradient}
            label="Scan QR"
            sublabel="Camera"
            icon={'⟗'}
            onPress={() => navigation.navigate('QRScanner')}
            style={styles.actionFlex}
          />
          <View style={{ width: 12 }} />
          <GradientButton
            colors={theme.primaryGradient}
            label="Enter UPI / Mobile"
            sublabel="Manual"
            icon={'₹'}
            onPress={() => navigation.navigate('PaymentEntry', {})}
            style={styles.actionFlex}
          />
        </View>

        <SectionHeader
          theme={theme}
          title="Pending Transactions"
          count={pending.length}
        />

        {pending.length === 0 ? (
          <EmptyState theme={theme} message="No pending payments. You are all caught up." />
        ) : (
          pending.map((item, index) => (
            <PendingTransactionItem
              key={item.id}
              item={item}
              theme={theme}
              isSyncing={syncingId === item.id}
              index={index}
            />
          ))
        )}

        <SectionHeader
          theme={theme}
          title="Recent History"
          actionLabel={history.length > 0 ? 'View all' : undefined}
          onAction={() => navigation.navigate('History')}
        />

        {history.length === 0 ? (
          <EmptyState theme={theme} message="No completed payments yet." />
        ) : (
          history.slice(0, 3).map((item, index) => (
            <PendingTransactionItem
              key={item.id}
              item={item}
              theme={theme}
              isCompleted
              index={index}
            />
          ))
        )}

        {!isOffline && pending.length > 0 && !isSyncing ? (
          <GradientButton
            variant="outline"
            label="Run sync now"
            textColor={theme.text}
            borderColor={theme.border}
            onPress={triggerManualSync}
            style={{ marginTop: 12 }}
          />
        ) : null}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 48 },
  header: { marginBottom: 18 },
  greeting: { fontSize: 13, fontWeight: '600', letterSpacing: 0.4, textTransform: 'uppercase', marginBottom: 4 },
  heading: { fontSize: 28, fontWeight: '800', letterSpacing: -0.5 },
  actionRow: { flexDirection: 'row', marginBottom: 24 },
  actionFlex: { flex: 1 },
  emptyState: {
    padding: 18,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    marginBottom: 14,
  },
  emptyText: { fontSize: 13 },
});

export default DashboardScreen;
