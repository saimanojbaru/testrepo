import { isUpiId, isMobileNumber, isValidRecipient, isValidAmount } from '../validation';

describe('isUpiId', () => {
  test('accepts standard handle@bank format', () => {
    expect(isUpiId('alice@okhdfcbank')).toBe(true);
    expect(isUpiId('alice.bob@upi')).toBe(true);
    expect(isUpiId('alice-bob_99@ybl')).toBe(true);
    expect(isUpiId('AliceBob123@PAYTM')).toBe(true);
  });

  test('trims surrounding whitespace before validating', () => {
    expect(isUpiId('  alice@okicici  ')).toBe(true);
  });

  test('rejects malformed strings', () => {
    expect(isUpiId('')).toBe(false);
    expect(isUpiId('alice')).toBe(false);
    expect(isUpiId('alice@')).toBe(false);
    expect(isUpiId('@bank')).toBe(false);
    expect(isUpiId('alice@bank1')).toBe(false);
    expect(isUpiId('alice@@bank')).toBe(false);
    expect(isUpiId('a@b')).toBe(false);
  });

  test('rejects null / undefined / non-string input safely', () => {
    expect(isUpiId(null)).toBe(false);
    expect(isUpiId(undefined)).toBe(false);
    expect(isUpiId(12345)).toBe(false);
  });
});

describe('isMobileNumber', () => {
  test('accepts 10-digit Indian mobiles starting with 6-9', () => {
    expect(isMobileNumber('9876543210')).toBe(true);
    expect(isMobileNumber('6000000000')).toBe(true);
    expect(isMobileNumber('7123456789')).toBe(true);
    expect(isMobileNumber('8888888888')).toBe(true);
  });

  test('strips internal whitespace', () => {
    expect(isMobileNumber('98765 43210')).toBe(true);
  });

  test('rejects bad numbers', () => {
    expect(isMobileNumber('1234567890')).toBe(false);
    expect(isMobileNumber('5876543210')).toBe(false);
    expect(isMobileNumber('98765432')).toBe(false);
    expect(isMobileNumber('98765432101')).toBe(false);
    expect(isMobileNumber('abcdefghij')).toBe(false);
    expect(isMobileNumber('')).toBe(false);
  });
});

describe('isValidRecipient', () => {
  test('returns true for either valid UPI ID or mobile', () => {
    expect(isValidRecipient('alice@okhdfcbank')).toBe(true);
    expect(isValidRecipient('9876543210')).toBe(true);
  });

  test('returns false for invalid input', () => {
    expect(isValidRecipient('not-a-recipient')).toBe(false);
    expect(isValidRecipient('')).toBe(false);
  });
});

describe('isValidAmount', () => {
  test('accepts positive amounts up to 100000 with up to 2 decimals', () => {
    expect(isValidAmount('1')).toBe(true);
    expect(isValidAmount('1.5')).toBe(true);
    expect(isValidAmount('1.50')).toBe(true);
    expect(isValidAmount('100000')).toBe(true);
    expect(isValidAmount('0.01')).toBe(true);
  });

  test('rejects zero, negatives, > 100000, > 2 decimals, non-numeric', () => {
    expect(isValidAmount('0')).toBe(false);
    expect(isValidAmount('0.00')).toBe(false);
    expect(isValidAmount('-5')).toBe(false);
    expect(isValidAmount('100000.01')).toBe(false);
    expect(isValidAmount('1.234')).toBe(false);
    expect(isValidAmount('abc')).toBe(false);
    expect(isValidAmount('')).toBe(false);
    expect(isValidAmount('1,234')).toBe(false);
  });
});
