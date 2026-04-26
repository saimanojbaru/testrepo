import React from 'react';
import { View, Text, StyleSheet, FlatList } from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import PendingTransactionItem from '../components/PendingTransactionItem';

const HistoryScreen = () => {
  const { theme, history } = useNetwork();

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Text style={[styles.heading, { color: theme.text }]}>Audit / History</Text>
      <Text style={[styles.subheading, { color: theme.subtext }]}>
        Payments that were queued offline and have since been synced.
      </Text>

      {history.length === 0 ? (
        <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}>
          <Text style={[styles.emptyText, { color: theme.subtext }]}>
            No completed payments yet. Once you go online, queued items will appear here.
          </Text>
        </View>
      ) : (
        <FlatList
          data={history}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <PendingTransactionItem item={item} theme={theme} isCompleted />}
          contentContainerStyle={{ paddingBottom: 24 }}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20 },
  heading: { fontSize: 22, fontWeight: '800', marginBottom: 4 },
  subheading: { fontSize: 13, marginBottom: 16 },
  empty: { padding: 16, borderRadius: 12, borderWidth: 1, alignItems: 'center' },
  emptyText: { fontSize: 13, textAlign: 'center' },
});

export default HistoryScreen;
