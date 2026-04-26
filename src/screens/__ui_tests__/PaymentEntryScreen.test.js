import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';

import PaymentEntryScreen from '../PaymentEntryScreen';
import { renderScreen } from './testHarness';

describe('PaymentEntryScreen', () => {
  it('shows the Save Offline CTA and inputs', async () => {
    const { Wrapper } = renderScreen(PaymentEntryScreen);
    const { findByText, getByTestId } = render(<Wrapper />);
    expect(await findByText('Save Offline')).toBeTruthy();
    expect(getByTestId('recipient-input')).toBeTruthy();
    expect(getByTestId('amount-input')).toBeTruthy();
    expect(getByTestId('note-input')).toBeTruthy();
  });

  it('shows validation errors when fields are blank', async () => {
    const { Wrapper } = renderScreen(PaymentEntryScreen);
    const { findByText } = render(<Wrapper />);
    fireEvent.press(await findByText('Save Offline'));
    await waitFor(() => findByText(/Enter a valid UPI ID/i));
    expect(await findByText(/Enter an amount between/i)).toBeTruthy();
  });

  it('clears the recipient error once a valid value is typed', async () => {
    const { Wrapper } = renderScreen(PaymentEntryScreen);
    const { findByText, getByTestId, queryByText } = render(<Wrapper />);
    fireEvent.press(await findByText('Save Offline'));
    await waitFor(() => findByText(/Enter a valid UPI ID/i));
    fireEvent.changeText(getByTestId('recipient-input'), 'alice@okicici');
    await waitFor(() => expect(queryByText(/Enter a valid UPI ID/i)).toBeNull());
  });

  it('pre-fills fields from route params', async () => {
    const { Wrapper } = renderScreen(PaymentEntryScreen, {
      recipient: 'merchant@okicici',
      amount: '123.45',
      note: 'Pre-filled',
    });
    const { getByTestId } = render(<Wrapper />);
    await waitFor(() => expect(getByTestId('recipient-input').props.value).toBe('merchant@okicici'));
    expect(getByTestId('amount-input').props.value).toBe('123.45');
    expect(getByTestId('note-input').props.value).toBe('Pre-filled');
  });
});
