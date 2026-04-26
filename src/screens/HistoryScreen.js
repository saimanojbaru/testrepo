import React from 'react';
import { View, Text, StyleSheet, FlatList } from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import PendingTransactionItem from '../components/PendingTransactionItem';
import SectionHeader from '../components/SectionHeader';

const HistoryScreen = () => {
  const { theme, history } = useNetwork();

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.headerWrap}>
        <Text style={[styles.heading, { color: theme.text }]}>Audit / History</Text>
        <Text style={[styles.subheading, { color: theme.subtext }]}>
          Payments that were captured offline and have since been synced.
        </Text>
      </View>

      {history.length === 0 ? (
        <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}>
          <Text style={[styles.emptyTitle, { color: theme.text }]}>Nothing here yet</Text>
          <Text style={[styles.emptyText, { color: theme.subtext }]}>
            Once you go online, queued items will sync and appear here.
          </Text>
        </View>
      ) : (
        <FlatList
          data={history}
          keyExtractor={(item) => item.id}
          ListHeaderComponent={
            <SectionHeader theme={theme} title="Completed" count={history.length} />
          }
          renderItem={({ item, index }) => (
            <PendingTransactionItem item={item} theme={theme} isCompleted index={index} />
          )}
          contentContainerStyle={{ paddingBottom: 32, paddingHorizontal: 20 }}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  headerWrap: { padding: 20, paddingBottom: 8 },
  heading: { fontSize: 26, fontWeight: '800', marginBottom: 4, letterSpacing: -0.4 },
  subheading: { fontSize: 13 },
  empty: {
    margin: 20,
    padding: 22,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
  },
  emptyTitle: { fontSize: 15, fontWeight: '700', marginBottom: 4 },
  emptyText: { fontSize: 13, textAlign: 'center' },
});

export default HistoryScreen;
