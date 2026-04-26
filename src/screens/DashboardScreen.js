import React from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, ScrollView } from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import PendingTransactionItem from '../components/PendingTransactionItem';

const DashboardScreen = ({ navigation }) => {
  const { theme, pending, history, isOffline, syncingId, triggerManualSync, isSyncing } = useNetwork();

  const renderPending = ({ item }) => (
    <PendingTransactionItem item={item} theme={theme} isSyncing={syncingId === item.id} />
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[styles.heading, { color: theme.text }]}>Offline UPI Capture</Text>
        <Text style={[styles.subheading, { color: theme.subtext }]}>
          {isOffline ? 'Working offline. Capture payments and we will sync them when you are back online.' : 'Connected. Tap an action below to capture a new payment.'}
        </Text>

        <View style={styles.actionRow}>
          <TouchableOpacity
            style={[styles.actionButton, { backgroundColor: theme.primary }]}
            onPress={() => navigation.navigate('QRScanner')}
            activeOpacity={0.85}
          >
            <Text style={[styles.actionIcon, { color: theme.primaryText }]}>{'▢'}</Text>
            <Text style={[styles.actionText, { color: theme.primaryText }]}>Scan QR</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.actionButton, { backgroundColor: theme.accent }]}
            onPress={() => navigation.navigate('PaymentEntry', {})}
            activeOpacity={0.85}
          >
            <Text style={[styles.actionIcon, { color: theme.primaryText }]}>{'₹'}</Text>
            <Text style={[styles.actionText, { color: theme.primaryText }]}>Enter Mobile / UPI ID</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Pending Transactions</Text>
          <Text style={[styles.sectionCount, { color: theme.subtext }]}>{pending.length}</Text>
        </View>

        {pending.length === 0 ? (
          <View style={[styles.emptyState, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <Text style={[styles.emptyText, { color: theme.subtext }]}>No pending payments. You are all caught up.</Text>
          </View>
        ) : (
          <FlatList
            data={pending}
            keyExtractor={(item) => item.id}
            renderItem={renderPending}
            scrollEnabled={false}
          />
        )}

        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Recent History</Text>
          <TouchableOpacity onPress={() => navigation.navigate('History')}>
            <Text style={[styles.link, { color: theme.accent }]}>View all</Text>
          </TouchableOpacity>
        </View>

        {history.length === 0 ? (
          <View style={[styles.emptyState, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <Text style={[styles.emptyText, { color: theme.subtext }]}>No completed payments yet.</Text>
          </View>
        ) : (
          history.slice(0, 3).map((item) => (
            <PendingTransactionItem key={item.id} item={item} theme={theme} isCompleted />
          ))
        )}

        {!isOffline && pending.length > 0 && !isSyncing ? (
          <TouchableOpacity
            style={[styles.secondaryButton, { borderColor: theme.border }]}
            onPress={triggerManualSync}
          >
            <Text style={[styles.secondaryText, { color: theme.text }]}>Run sync now</Text>
          </TouchableOpacity>
        ) : null}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 40 },
  heading: { fontSize: 26, fontWeight: '800', marginBottom: 4 },
  subheading: { fontSize: 14, marginBottom: 20 },
  actionRow: { flexDirection: 'row', gap: 12, marginBottom: 24 },
  actionButton: {
    flex: 1,
    borderRadius: 16,
    paddingVertical: 22,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionIcon: { fontSize: 28, marginBottom: 6 },
  actionText: { fontSize: 14, fontWeight: '700', textAlign: 'center' },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
    marginBottom: 12,
  },
  sectionTitle: { fontSize: 18, fontWeight: '700' },
  sectionCount: { fontSize: 14, fontWeight: '600' },
  link: { fontSize: 14, fontWeight: '600' },
  emptyState: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    marginBottom: 12,
  },
  emptyText: { fontSize: 13 },
  secondaryButton: {
    marginTop: 16,
    paddingVertical: 12,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
  },
  secondaryText: { fontSize: 14, fontWeight: '600' },
});

export default DashboardScreen;
