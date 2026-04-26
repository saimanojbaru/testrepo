import React from 'react';
import { render, waitFor } from '@testing-library/react-native';

import SuccessScreen from '../SuccessScreen';
import { renderScreen } from './testHarness';

describe('SuccessScreen', () => {
  it('renders the offline-specific success message and entry details', async () => {
    const entry = {
      id: 'x',
      recipient: 'shop@upi',
      amount: 99,
      note: 'Coffee',
      timestamp: Date.now(),
      status: 'pending',
    };
    const { Wrapper } = renderScreen(SuccessScreen, { entry });
    const { findByText } = render(<Wrapper />);
    await waitFor(() => findByText('Payment Intent Saved!'));
    expect(await findByText(/processed automatically when your internet returns/i)).toBeTruthy();
    expect(await findByText('shop@upi')).toBeTruthy();
    expect(await findByText('₹99.00')).toBeTruthy();
    expect(await findByText('QUEUED')).toBeTruthy();
  });
});
