import React from 'react';
import { render } from '@testing-library/react-native';

import { NetworkProvider } from '../../context/NetworkContext';
import NetworkBanner from '../NetworkBanner';

const wrap = (ui) => <NetworkProvider>{ui}</NetworkProvider>;

describe('NetworkBanner', () => {
  it('renders the online message by default', async () => {
    const { findByText } = render(wrap(<NetworkBanner />));
    const node = await findByText(/Online\.|Syncing/i);
    expect(node).toBeTruthy();
  });
});
