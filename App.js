import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StyleSheet, View } from 'react-native';

import { NetworkProvider, useNetwork } from './src/context/NetworkContext';
import NetworkBanner from './src/components/NetworkBanner';
import DashboardScreen from './src/screens/DashboardScreen';
import PaymentEntryScreen from './src/screens/PaymentEntryScreen';
import QRScannerScreen from './src/screens/QRScannerScreen';
import SuccessScreen from './src/screens/SuccessScreen';
import HistoryScreen from './src/screens/HistoryScreen';

const Stack = createNativeStackNavigator();

const Shell = () => {
  const { theme, isOffline } = useNetwork();
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.bannerBg }]} edges={['top']}>
      <StatusBar style={isOffline ? 'dark' : 'light'} />
      <NetworkBanner />
      <View style={[styles.body, { backgroundColor: theme.background }]}>
        <NavigationContainer
          theme={{
            dark: false,
            colors: {
              primary: theme.primary,
              background: theme.background,
              card: theme.surface,
              text: theme.text,
              border: theme.border,
              notification: theme.warning,
            },
          }}
        >
          <Stack.Navigator
            screenOptions={{
              headerStyle: { backgroundColor: theme.surface },
              headerTitleStyle: { color: theme.text, fontWeight: '700' },
              headerTintColor: theme.text,
              contentStyle: { backgroundColor: theme.background },
            }}
          >
            <Stack.Screen name="Dashboard" component={DashboardScreen} options={{ title: 'Home' }} />
            <Stack.Screen name="PaymentEntry" component={PaymentEntryScreen} options={{ title: 'New Payment' }} />
            <Stack.Screen name="QRScanner" component={QRScannerScreen} options={{ title: 'Scan QR' }} />
            <Stack.Screen
              name="Success"
              component={SuccessScreen}
              options={{ title: 'Saved Offline', headerBackVisible: false }}
            />
            <Stack.Screen name="History" component={HistoryScreen} options={{ title: 'History' }} />
          </Stack.Navigator>
        </NavigationContainer>
      </View>
    </SafeAreaView>
  );
};

export default function App() {
  return (
    <SafeAreaProvider>
      <NetworkProvider>
        <Shell />
      </NetworkProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  body: { flex: 1 },
});
