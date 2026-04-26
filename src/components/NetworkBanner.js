import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated, Easing } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { useNetwork } from '../context/NetworkContext';

const PulsingDot = ({ color }) => {
  const scale = useRef(new Animated.Value(1)).current;
  const opacity = useRef(new Animated.Value(0.7)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.parallel([
        Animated.sequence([
          Animated.timing(scale, { toValue: 1.6, duration: 900, easing: Easing.out(Easing.quad), useNativeDriver: true }),
          Animated.timing(scale, { toValue: 1.0, duration: 900, easing: Easing.in(Easing.quad), useNativeDriver: true }),
        ]),
        Animated.sequence([
          Animated.timing(opacity, { toValue: 0.2, duration: 900, useNativeDriver: true }),
          Animated.timing(opacity, { toValue: 0.7, duration: 900, useNativeDriver: true }),
        ]),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [scale, opacity]);

  return (
    <View style={styles.dotWrap}>
      <Animated.View style={[styles.pulse, { backgroundColor: color, transform: [{ scale }], opacity }]} />
      <View style={[styles.dot, { backgroundColor: color }]} />
    </View>
  );
};

const NetworkBanner = () => {
  const { isOffline, isSyncing, theme, pending } = useNetwork();

  const message = isOffline
    ? 'You are offline. Payments will be queued.'
    : isSyncing && pending.length > 0
      ? `Syncing ${pending.length} pending payment${pending.length === 1 ? '' : 's'}…`
      : 'Online. Syncing pending payments…';

  const dotColor = isOffline ? '#ffffff' : '#a7f3d0';

  return (
    <LinearGradient
      colors={theme.bannerGradient}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 0 }}
      style={styles.container}
    >
      <PulsingDot color={dotColor} />
      <Text style={[styles.text, { color: theme.bannerText }]} numberOfLines={1}>
        {message}
      </Text>
    </LinearGradient>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    paddingVertical: 12,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  text: {
    fontSize: 13,
    fontWeight: '600',
    flexShrink: 1,
    letterSpacing: 0.2,
  },
  dotWrap: {
    width: 14,
    height: 14,
    marginRight: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  pulse: {
    position: 'absolute',
    width: 14,
    height: 14,
    borderRadius: 7,
  },
});

export default NetworkBanner;
