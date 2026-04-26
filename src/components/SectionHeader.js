import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';

const SectionHeader = ({ theme, title, count, actionLabel, onAction }) => {
  return (
    <View style={styles.row}>
      <View style={styles.titleRow}>
        <Text style={[styles.title, { color: theme.text }]}>{title}</Text>
        {typeof count === 'number' ? (
          <View style={[styles.countPill, { backgroundColor: theme.divider }]}>
            <Text style={[styles.countText, { color: theme.subtext }]}>{count}</Text>
          </View>
        ) : null}
      </View>
      {actionLabel ? (
        <Pressable onPress={onAction} hitSlop={8}>
          <Text style={[styles.action, { color: theme.accent }]}>{actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 4,
    marginBottom: 14,
  },
  titleRow: { flexDirection: 'row', alignItems: 'center' },
  title: { fontSize: 16, fontWeight: '700', letterSpacing: 0.2 },
  countPill: {
    marginLeft: 8,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
  },
  countText: { fontSize: 11, fontWeight: '700' },
  action: { fontSize: 13, fontWeight: '600' },
});

export default SectionHeader;
