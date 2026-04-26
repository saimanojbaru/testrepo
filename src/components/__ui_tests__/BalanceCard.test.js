import React from 'react';
import { render } from '@testing-library/react-native';

import BalanceCard from '../BalanceCard';
import { getTheme } from '../../theme/colors';

describe('BalanceCard', () => {
  it('shows pending total, pending count and completed count', () => {
    const { getByText } = render(
      <BalanceCard theme={getTheme(false)} isOffline={false} pendingCount={3} pendingTotal={1234.5} completedCount={7} />,
    );
    expect(getByText('₹1234.50')).toBeTruthy();
    expect(getByText('3')).toBeTruthy();
    expect(getByText('7')).toBeTruthy();
    expect(getByText('LIVE')).toBeTruthy();
  });

  it('switches status pill when offline', () => {
    const { getByText } = render(
      <BalanceCard theme={getTheme(true)} isOffline pendingCount={0} pendingTotal={0} completedCount={0} />,
    );
    expect(getByText('OFFLINE')).toBeTruthy();
    expect(getByText('₹0.00')).toBeTruthy();
  });
});
