import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';

import GradientButton from '../GradientButton';

describe('GradientButton', () => {
  it('renders the label and fires onPress', () => {
    const onPress = jest.fn();
    const { getByText } = render(
      <GradientButton colors={['#000', '#111']} label="Tap me" onPress={onPress} />,
    );
    fireEvent.press(getByText('Tap me'));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('does not fire onPress when disabled', () => {
    const onPress = jest.fn();
    const { getByText } = render(
      <GradientButton colors={['#000', '#111']} label="Disabled" onPress={onPress} disabled />,
    );
    fireEvent.press(getByText('Disabled'));
    expect(onPress).not.toHaveBeenCalled();
  });

  it('renders the outline variant without crashing', () => {
    const { getByText } = render(
      <GradientButton variant="outline" textColor="#000" label="Outline" onPress={() => {}} />,
    );
    expect(getByText('Outline')).toBeTruthy();
  });

  it('renders an optional sublabel and icon', () => {
    const { getByText } = render(
      <GradientButton colors={['#000', '#111']} label="Main" sublabel="Helper" icon="₹" onPress={() => {}} />,
    );
    expect(getByText('Main')).toBeTruthy();
    expect(getByText('Helper')).toBeTruthy();
    expect(getByText('₹')).toBeTruthy();
  });
});
