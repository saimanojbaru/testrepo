import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import { formatCurrency } from '../utils/format';

const SuccessScreen = ({ navigation, route }) => {
  const { theme, isOffline } = useNetwork();
  const entry = route?.params?.entry;

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }]}>
        <View style={[styles.checkCircle, { borderColor: theme.success }]}>
          <Text style={[styles.checkMark, { color: theme.success }]}>✓</Text>
        </View>
        <Text style={[styles.title, { color: theme.text }]}>Payment Intent Saved!</Text>
        <Text style={[styles.subtitle, { color: theme.subtext }]}>
          This will be processed automatically when your internet returns.
        </Text>

        {entry ? (
          <View style={[styles.detail, { backgroundColor: theme.background, borderColor: theme.border }]}>
            <View style={styles.detailRow}>
              <Text style={[styles.detailLabel, { color: theme.subtext }]}>To</Text>
              <Text style={[styles.detailValue, { color: theme.text }]} numberOfLines={1}>
                {entry.recipient}
              </Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={[styles.detailLabel, { color: theme.subtext }]}>Amount</Text>
              <Text style={[styles.detailValue, { color: theme.text }]}>{formatCurrency(entry.amount)}</Text>
            </View>
            {entry.note ? (
              <View style={styles.detailRow}>
                <Text style={[styles.detailLabel, { color: theme.subtext }]}>Note</Text>
                <Text style={[styles.detailValue, { color: theme.text }]} numberOfLines={2}>
                  {entry.note}
                </Text>
              </View>
            ) : null}
            <View style={styles.detailRow}>
              <Text style={[styles.detailLabel, { color: theme.subtext }]}>Status</Text>
              <View style={[styles.statusPill, { backgroundColor: theme.pendingBg }]}>
                <Text style={[styles.statusText, { color: theme.pendingText }]}>QUEUED</Text>
              </View>
            </View>
          </View>
        ) : null}

        <Text style={[styles.networkNote, { color: theme.subtext }]}>
          {isOffline
            ? 'You are still offline. We will sync as soon as your network is back.'
            : 'You are online — we will start syncing this and any other queued payments shortly.'}
        </Text>

        <TouchableOpacity
          style={[styles.button, { backgroundColor: theme.primary }]}
          onPress={() => navigation.popToTop()}
          activeOpacity={0.85}
        >
          <Text style={[styles.buttonText, { color: theme.primaryText }]}>Back to Dashboard</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.secondary, { borderColor: theme.border }]}
          onPress={() => navigation.replace('PaymentEntry', {})}
          activeOpacity={0.85}
        >
          <Text style={[styles.secondaryText, { color: theme.text }]}>Capture another payment</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, justifyContent: 'center' },
  card: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 24,
    alignItems: 'center',
  },
  checkCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  checkMark: { fontSize: 40, fontWeight: '800' },
  title: { fontSize: 22, fontWeight: '800', textAlign: 'center', marginBottom: 6 },
  subtitle: { fontSize: 14, textAlign: 'center', lineHeight: 20, marginBottom: 18 },
  detail: {
    width: '100%',
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    marginBottom: 16,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
  },
  detailLabel: { fontSize: 13, fontWeight: '600' },
  detailValue: { fontSize: 14, fontWeight: '600', maxWidth: '70%', textAlign: 'right' },
  statusPill: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  statusText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.5 },
  networkNote: { fontSize: 12, textAlign: 'center', marginBottom: 16, lineHeight: 16 },
  button: {
    paddingVertical: 14,
    borderRadius: 12,
    alignSelf: 'stretch',
    alignItems: 'center',
  },
  buttonText: { fontSize: 15, fontWeight: '700' },
  secondary: {
    marginTop: 10,
    paddingVertical: 14,
    borderRadius: 12,
    borderWidth: 1,
    alignSelf: 'stretch',
    alignItems: 'center',
  },
  secondaryText: { fontSize: 14, fontWeight: '600' },
});

export default SuccessScreen;
