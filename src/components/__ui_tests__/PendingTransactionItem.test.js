import React from 'react';
import { render } from '@testing-library/react-native';

import PendingTransactionItem from '../PendingTransactionItem';
import { getTheme } from '../../theme/colors';

const theme = getTheme(false);

describe('PendingTransactionItem', () => {
  const item = {
    id: 'a1',
    recipient: 'alice@okhdfcbank',
    amount: 250.5,
    timestamp: 1700000000000,
    status: 'pending',
    note: 'Tea & samosa',
  };

  it('renders recipient, amount and PENDING badge by default', () => {
    const { getByText } = render(<PendingTransactionItem item={item} theme={theme} />);
    expect(getByText('alice@okhdfcbank')).toBeTruthy();
    expect(getByText('₹250.50')).toBeTruthy();
    expect(getByText('PENDING')).toBeTruthy();
  });

  it('shows COMPLETED badge when isCompleted', () => {
    const { getByText } = render(<PendingTransactionItem item={item} theme={theme} isCompleted />);
    expect(getByText('COMPLETED')).toBeTruthy();
  });

  it('shows SYNCING badge when isSyncing', () => {
    const { getByText } = render(<PendingTransactionItem item={item} theme={theme} isSyncing />);
    expect(getByText('SYNCING')).toBeTruthy();
  });

  it('renders avatar initial uppercased', () => {
    const { getByText } = render(<PendingTransactionItem item={{ ...item, recipient: 'bob@upi' }} theme={theme} />);
    expect(getByText('B')).toBeTruthy();
  });
});
