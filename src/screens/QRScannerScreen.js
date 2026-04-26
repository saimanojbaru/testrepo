import React, { useState, useRef, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Linking } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';

import { useNetwork } from '../context/NetworkContext';
import { parseUpiUri } from '../utils/upiParser';

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
        <Text style={[styles.text, { color: theme.text }]}>Loading camera permissions...</Text>
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
          <TouchableOpacity style={[styles.button, { backgroundColor: theme.primary }]} onPress={requestPermission}>
            <Text style={[styles.buttonText, { color: theme.primaryText }]}>Grant permission</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[styles.button, { backgroundColor: theme.primary }]}
            onPress={() => Linking.openSettings()}
          >
            <Text style={[styles.buttonText, { color: theme.primaryText }]}>Open settings</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.linkButton}>
          <Text style={[styles.link, { color: theme.accent }]}>Go back</Text>
        </TouchableOpacity>
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
        <View style={styles.frame} />
        <Text style={styles.hint}>Align the UPI QR within the frame</Text>
      </View>
      {error ? (
        <View style={[styles.errorBar, { backgroundColor: theme.danger }]}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      ) : null}
      <View style={styles.bottomBar}>
        {scanned ? (
          <TouchableOpacity style={[styles.button, { backgroundColor: theme.primary }]} onPress={resetScanner}>
            <Text style={[styles.buttonText, { color: theme.primaryText }]}>Scan again</Text>
          </TouchableOpacity>
        ) : null}
        <TouchableOpacity
          style={[styles.button, { backgroundColor: theme.accent }]}
          onPress={() => navigation.replace('PaymentEntry', {})}
        >
          <Text style={[styles.buttonText, { color: theme.primaryText }]}>Enter manually</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  center: { flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 8, textAlign: 'center' },
  text: { fontSize: 14, textAlign: 'center', marginBottom: 16 },
  overlay: { ...StyleSheet.absoluteFillObject, alignItems: 'center', justifyContent: 'center' },
  frame: {
    width: 240,
    height: 240,
    borderColor: '#fff',
    borderWidth: 3,
    borderRadius: 16,
    backgroundColor: 'transparent',
  },
  hint: { color: '#fff', marginTop: 18, fontSize: 14, fontWeight: '600' },
  errorBar: { position: 'absolute', top: 16, left: 16, right: 16, padding: 12, borderRadius: 10 },
  errorText: { color: '#fff', textAlign: 'center', fontWeight: '600' },
  bottomBar: {
    position: 'absolute',
    left: 16,
    right: 16,
    bottom: 24,
    gap: 10,
  },
  button: {
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonText: { fontSize: 15, fontWeight: '700' },
  linkButton: { marginTop: 12 },
  link: { fontSize: 14, fontWeight: '600' },
});

export default QRScannerScreen;
