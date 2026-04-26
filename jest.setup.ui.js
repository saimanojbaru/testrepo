/* eslint-disable global-require */

jest.mock('@react-native-async-storage/async-storage', () => {
  const store = new Map();
  return {
    __esModule: true,
    default: {
      getItem: jest.fn(async (key) => (store.has(key) ? store.get(key) : null)),
      setItem: jest.fn(async (key, value) => {
        store.set(key, String(value));
      }),
      removeItem: jest.fn(async (key) => {
        store.delete(key);
      }),
      multiRemove: jest.fn(async (keys) => {
        keys.forEach((key) => store.delete(key));
      }),
      clear: jest.fn(async () => store.clear()),
    },
  };
});

jest.mock('@react-native-community/netinfo', () => {
  const listeners = new Set();
  const fetchState = jest.fn(async () => ({ isConnected: true, isInternetReachable: true }));
  return {
    __esModule: true,
    default: {
      addEventListener: jest.fn((listener) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
      }),
      fetch: fetchState,
    },
    addEventListener: jest.fn((listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }),
    fetch: fetchState,
  };
});

jest.mock('expo-camera', () => {
  const React = require('react');
  const { View, Text } = require('react-native');
  const CameraView = (props) => React.createElement(View, { testID: 'mock-camera-view' }, props.children);
  return {
    CameraView,
    useCameraPermissions: () => [{ granted: true, canAskAgain: true }, jest.fn()],
    Camera: { requestCameraPermissionsAsync: jest.fn(async () => ({ status: 'granted' })) },
    __MockText: Text,
  };
});

jest.mock('expo-linear-gradient', () => {
  const React = require('react');
  const { View } = require('react-native');
  return {
    LinearGradient: (props) => React.createElement(View, props, props.children),
  };
});

jest.mock('expo-status-bar', () => {
  const React = require('react');
  const { View } = require('react-native');
  return { StatusBar: () => React.createElement(View) };
});

jest.mock('react-native/Libraries/Animated/NativeAnimatedHelper');
