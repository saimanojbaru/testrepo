import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { NetworkProvider } from '../../context/NetworkContext';

const Stack = createNativeStackNavigator();

const StubScreen = () => null;

export const renderScreen = (ScreenComponent, params = {}) => {
  const safeAreaMetrics = {
    frame: { x: 0, y: 0, width: 390, height: 844 },
    insets: { top: 0, left: 0, right: 0, bottom: 0 },
  };
  return {
    Wrapper: () => (
      <SafeAreaProvider initialMetrics={safeAreaMetrics}>
        <NetworkProvider>
          <NavigationContainer>
            <Stack.Navigator screenOptions={{ headerShown: false }}>
              <Stack.Screen name="UnderTest" initialParams={params} component={ScreenComponent} />
              <Stack.Screen name="PaymentEntry" component={StubScreen} />
              <Stack.Screen name="QRScanner" component={StubScreen} />
              <Stack.Screen name="Success" component={StubScreen} />
              <Stack.Screen name="History" component={StubScreen} />
              <Stack.Screen name="Dashboard" component={StubScreen} />
            </Stack.Navigator>
          </NavigationContainer>
        </NetworkProvider>
      </SafeAreaProvider>
    ),
  };
};
