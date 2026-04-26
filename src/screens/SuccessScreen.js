import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated, Easing } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { useNetwork } from '../context/NetworkContext';
import { formatCurrency } from '../utils/format';
import GradientButton from '../components/GradientButton';
import { elevation } from '../theme/colors';

const SuccessScreen = ({ navigation, route }) => {
  const { theme, isOffline } = useNetwork();
  const entry = route?.params?.entry;

  const scale = useRef(new Animated.Value(0.5)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const ringScale = useRef(new Animated.Value(0.6)).current;
  const ringOpacity = useRef(new Animated.Value(0.6)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(scale, { toValue: 1, useNativeDriver: true, speed: 12, bounciness: 10 }),
      Animated.timing(opacity, { toValue: 1, duration: 400, useNativeDriver: true }),
    ]).start();
    Animated.loop(
      Animated.parallel([
        Animated.timing(ringScale, { toValue: 1.6, duration: 1600, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
        Animated.timing(ringOpacity, { toValue: 0, duration: 1600, useNativeDriver: true }),
      ]),
      { resetBeforeIteration: true },
    ).start();
  }, [scale, opacity, ringScale, ringOpacity]);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <Animated.View style={[{ opacity }, styles.cardWrap]}>
        <LinearGradient
          colors={isOffline ? ['#1f2937', '#111827'] : ['#0b6e4f', '#064e36']}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={[styles.card, elevation.large]}
        >
          <View style={styles.checkWrap}>
            <Animated.View
              style={[
                styles.ring,
                { transform: [{ scale: ringScale }], opacity: ringOpacity },
              ]}
            />
            <Animated.View style={[styles.checkCircle, { transform: [{ scale }] }]}>
              <Text style={styles.checkMark}>✓</Text>
            </Animated.View>
          </View>

          <Text style={styles.title}>Payment Intent Saved!</Text>
          <Text style={styles.subtitle}>
            This will be processed automatically when your internet returns.
          </Text>

          {entry ? (
            <View style={styles.detail}>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>To</Text>
                <Text style={styles.detailValue} numberOfLines={1}>{entry.recipient}</Text>
              </View>
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Amount</Text>
                <Text style={[styles.detailValue, styles.amountValue]}>{formatCurrency(entry.amount)}</Text>
              </View>
              {entry.note ? (
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Note</Text>
                  <Text style={styles.detailValue} numberOfLines={2}>{entry.note}</Text>
                </View>
              ) : null}
              <View style={styles.detailRow}>
                <Text style={styles.detailLabel}>Status</Text>
                <View style={styles.statusPill}>
                  <View style={styles.statusDot} />
                  <Text style={styles.statusText}>QUEUED</Text>
                </View>
              </View>
            </View>
          ) : null}

          <Text style={styles.networkNote}>
            {isOffline
              ? 'You are still offline. Sync starts automatically when your network returns.'
              : 'You are online — sync starts shortly.'}
          </Text>
        </LinearGradient>

        <GradientButton
          colors={theme.primaryGradient}
          label="Back to Dashboard"
          onPress={() => navigation.popToTop()}
          style={{ marginTop: 18 }}
        />
        <GradientButton
          variant="outline"
          textColor={theme.text}
          borderColor={theme.border}
          label="Capture another"
          onPress={() => navigation.replace('PaymentEntry', {})}
          style={{ marginTop: 10 }}
        />
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20, justifyContent: 'center' },
  cardWrap: { width: '100%' },
  card: {
    borderRadius: 22,
    padding: 26,
    alignItems: 'center',
    overflow: 'hidden',
  },
  checkWrap: {
    width: 96,
    height: 96,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 18,
  },
  ring: {
    position: 'absolute',
    width: 96,
    height: 96,
    borderRadius: 48,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.6)',
  },
  checkCircle: {
    width: 78,
    height: 78,
    borderRadius: 39,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.4)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkMark: { fontSize: 42, color: '#ffffff', fontWeight: '800' },
  title: { fontSize: 22, fontWeight: '800', textAlign: 'center', marginBottom: 6, color: '#ffffff', letterSpacing: -0.3 },
  subtitle: { fontSize: 13, textAlign: 'center', lineHeight: 19, marginBottom: 22, color: 'rgba(255,255,255,0.75)' },
  detail: {
    width: '100%',
    borderRadius: 14,
    padding: 14,
    marginBottom: 16,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 7,
  },
  detailLabel: { fontSize: 12, fontWeight: '600', color: 'rgba(255,255,255,0.65)', letterSpacing: 0.4, textTransform: 'uppercase' },
  detailValue: { fontSize: 14, fontWeight: '600', maxWidth: '70%', textAlign: 'right', color: '#ffffff' },
  amountValue: { fontSize: 18, fontWeight: '800' },
  statusPill: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, backgroundColor: 'rgba(245,158,11,0.25)' },
  statusDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#fbbf24', marginRight: 6 },
  statusText: { fontSize: 10, fontWeight: '800', letterSpacing: 0.7, color: '#fde68a' },
  networkNote: { fontSize: 12, textAlign: 'center', color: 'rgba(255,255,255,0.7)', lineHeight: 17 },
});

export default SuccessScreen;
