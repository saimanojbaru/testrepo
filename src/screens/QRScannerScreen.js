import React, { useState, useRef, useEffect } from 'react';
import { View, Text, StyleSheet, Linking, Animated, Easing } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';

import { useNetwork } from '../context/NetworkContext';
import { parseUpiUri } from '../utils/upiParser';
import GradientButton from '../components/GradientButton';

const ScanLine = () => {
  const translateY = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(translateY, { toValue: 220, duration: 1800, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
        Animated.timing(translateY, { toValue: 0, duration: 1800, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [translateY]);

  return <Animated.View style={[styles.scanLine, { transform: [{ translateY }] }]} />;
};

const QRScannerScreen = ({ navigation }) => {
  const { theme } = useNetwork();
  const [permission, requestPermission] = useCameraPermissions();
  const [scanned, setScanned] = useState(false);
  const [error, setError] = useState('');
  const handledRef = useRef(false);

  useEffect(() => {
    if (permission && !permission.granted && permission.canAskAgain) {
      requestPermission();
    }
  }, [permission, requestPermission]);

  const handleScan = ({ data }) => {
    if (handledRef.current) return;
    const parsed = parseUpiUri(data);
    if (!parsed) {
      setError('Not a valid UPI QR code. Try again or enter details manually.');
      setScanned(true);
      return;
    }
    handledRef.current = true;
    setScanned(true);
    navigation.replace('PaymentEntry', {
      recipient: parsed.payeeAddress,
      amount: parsed.amount,
      note: parsed.note || (parsed.payeeName ? `Pay to ${parsed.payeeName}` : ''),
    });
  };

  const resetScanner = () => {
    setScanned(false);
    setError('');
    handledRef.current = false;
  };

  if (!permission) {
    return (
      <View style={[styles.center, { backgroundColor: theme.background }]}>
        <Text style={[styles.text, { color: theme.text }]}>Loading camera permissions…</Text>
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={[styles.center, { backgroundColor: theme.background }]}>
        <Text style={[styles.title, { color: theme.text }]}>Camera permission needed</Text>
        <Text style={[styles.text, { color: theme.subtext }]}>
          We use the camera only to scan UPI QR codes locally on your device.
        </Text>
        {permission.canAskAgain ? (
          <GradientButton
            colors={theme.primaryGradient}
            label="Grant permission"
            onPress={requestPermission}
            style={styles.permissionButton}
          />
        ) : (
          <GradientButton
            colors={theme.primaryGradient}
            label="Open settings"
            onPress={() => Linking.openSettings()}
            style={styles.permissionButton}
          />
        )}
        <GradientButton
          variant="outline"
          textColor={theme.text}
          borderColor={theme.border}
          label="Go back"
          onPress={() => navigation.goBack()}
          style={[styles.permissionButton, { marginTop: 10 }]}
        />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: '#000' }]}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
        onBarcodeScanned={scanned ? undefined : handleScan}
      />
      <View style={styles.overlay} pointerEvents="none">
        <View style={styles.frameWrap}>
          <View style={styles.frame}>
            <View style={[styles.corner, styles.cornerTL]} />
            <View style={[styles.corner, styles.cornerTR]} />
            <View style={[styles.corner, styles.cornerBL]} />
            <View style={[styles.corner, styles.cornerBR]} />
            {!scanned ? <ScanLine /> : null}
          </View>
        </View>
        <Text style={styles.hint}>Align the UPI QR within the frame</Text>
      </View>
      {error ? (
        <View style={[styles.errorBar, { backgroundColor: theme.danger }]}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}
      <View style={styles.bottomBar}>
        {scanned ? (
          <GradientButton
            colors={theme.primaryGradient}
            label="Scan again"
            onPress={resetScanner}
            style={{ marginBottom: 10 }}
          />
        ) : null}
        <GradientButton
          variant="outline"
          textColor="#ffffff"
          borderColor="rgba(255,255,255,0.5)"
          label="Enter manually"
          onPress={() => navigation.replace('PaymentEntry', {})}
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  center: { flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 8, textAlign: 'center' },
  text: { fontSize: 14, textAlign: 'center', marginBottom: 16, lineHeight: 20 },
  permissionButton: { alignSelf: 'stretch', marginTop: 4 },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: 'center', justifyContent: 'center' },
  frameWrap: { width: 260, height: 260, alignItems: 'center', justifyContent: 'center' },
  frame: {
    width: 240,
    height: 240,
    borderRadius: 18,
    overflow: 'hidden',
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 32,
    height: 32,
    borderColor: '#ffffff',
  },
  cornerTL: { top: 0, left: 0, borderTopWidth: 4, borderLeftWidth: 4, borderTopLeftRadius: 16 },
  cornerTR: { top: 0, right: 0, borderTopWidth: 4, borderRightWidth: 4, borderTopRightRadius: 16 },
  cornerBL: { bottom: 0, left: 0, borderBottomWidth: 4, borderLeftWidth: 4, borderBottomLeftRadius: 16 },
  cornerBR: { bottom: 0, right: 0, borderBottomWidth: 4, borderRightWidth: 4, borderBottomRightRadius: 16 },
  scanLine: {
    position: 'absolute',
    left: 12,
    right: 12,
    height: 2,
    backgroundColor: '#22d3ee',
    shadowColor: '#22d3ee',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.9,
    shadowRadius: 8,
  },
  hint: { color: '#ffffff', marginTop: 22, fontSize: 14, fontWeight: '600', letterSpacing: 0.3 },
  errorBar: { position: 'absolute', top: 16, left: 16, right: 16, padding: 12, borderRadius: 12 },
  errorText: { color: '#ffffff', textAlign: 'center', fontWeight: '600' },
  bottomBar: { position: 'absolute', left: 18, right: 18, bottom: 28 },
});

export default QRScannerScreen;
