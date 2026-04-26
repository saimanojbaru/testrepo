import React from 'react';
import { render, waitFor } from '@testing-library/react-native';

import HistoryScreen from '../HistoryScreen';
import { renderScreen } from './testHarness';

describe('HistoryScreen', () => {
  it('shows empty state when there is no history', async () => {
    const { Wrapper } = renderScreen(HistoryScreen);
    const { findByText } = render(<Wrapper />);
    await waitFor(() => findByText('Audit / History'));
    expect(await findByText('Nothing here yet')).toBeTruthy();
  });
});
