import React, { useRef } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

import { elevation } from '../theme/colors';

const GradientButton = ({
  colors,
  onPress,
  label,
  sublabel,
  icon,
  textColor = '#ffffff',
  size = 'md',
  variant = 'gradient',
  borderColor,
  disabled = false,
  style,
}) => {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePressIn = () => {
    Animated.spring(scale, { toValue: 0.97, useNativeDriver: true, speed: 40, bounciness: 6 }).start();
  };
  const handlePressOut = () => {
    Animated.spring(scale, { toValue: 1, useNativeDriver: true, speed: 40, bounciness: 6 }).start();
  };

  const padding = size === 'lg' ? 22 : size === 'sm' ? 12 : 16;

  const inner = (
    <View style={[styles.inner, { paddingVertical: padding }]}>
      {icon ? <Text style={[styles.icon, { color: textColor }]}>{icon}</Text> : null}
      <Text style={[styles.label, { color: textColor }]}>{label}</Text>
      {sublabel ? <Text style={[styles.sublabel, { color: textColor, opacity: 0.85 }]}>{sublabel}</Text> : null}
    </View>
  );

  return (
    <Animated.View style={[{ transform: [{ scale }], opacity: disabled ? 0.5 : 1 }, style]}>
      <Pressable onPress={onPress} onPressIn={handlePressIn} onPressOut={handlePressOut} disabled={disabled}>
        {variant === 'outline' ? (
          <View style={[styles.outline, { borderColor: borderColor || textColor }]}>{inner}</View>
        ) : (
          <LinearGradient
            colors={colors}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={[styles.gradient, elevation.medium]}
          >
            {inner}
          </LinearGradient>
        )}
      </Pressable>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  gradient: {
    borderRadius: 18,
    overflow: 'hidden',
  },
  outline: {
    borderRadius: 18,
    borderWidth: 1.5,
  },
  inner: {
    paddingHorizontal: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: { fontSize: 26, marginBottom: 6 },
  label: { fontSize: 15, fontWeight: '700', letterSpacing: 0.3, textAlign: 'center' },
  sublabel: { fontSize: 11, fontWeight: '500', marginTop: 4, letterSpacing: 0.2 },
});

export default GradientButton;
