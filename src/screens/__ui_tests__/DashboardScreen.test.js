import React from 'react';
import { render, waitFor } from '@testing-library/react-native';

import DashboardScreen from '../DashboardScreen';
import { renderScreen } from './testHarness';

describe('DashboardScreen', () => {
  it('renders the heading and the two action buttons', async () => {
    const { Wrapper } = renderScreen(DashboardScreen);
    const { findByText } = render(<Wrapper />);
    await waitFor(() => findByText('Offline UPI Capture'));
    expect(await findByText('Scan QR')).toBeTruthy();
    expect(await findByText('Enter UPI / Mobile')).toBeTruthy();
    expect(await findByText('Pending Transactions')).toBeTruthy();
  });

  it('shows the empty state when the queue is empty', async () => {
    const { Wrapper } = renderScreen(DashboardScreen);
    const { findByText } = render(<Wrapper />);
    expect(await findByText(/No pending payments/i)).toBeTruthy();
    expect(await findByText(/No completed payments/i)).toBeTruthy();
  });
});
