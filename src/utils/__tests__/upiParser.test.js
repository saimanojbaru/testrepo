import { parseUpiUri } from '../upiParser';

describe('parseUpiUri', () => {
  test('parses a full UPI URI with all common params', () => {
    const uri = 'upi://pay?pa=merchant@okicici&pn=Test%20Merchant&am=199.50&cu=INR&tn=Order%20%23123';
    expect(parseUpiUri(uri)).toEqual({
      payeeAddress: 'merchant@okicici',
      payeeName: 'Test Merchant',
      amount: '199.50',
      note: 'Order #123',
      currency: 'INR',
    });
  });

  test('parses minimal URI with only payeeAddress', () => {
    const result = parseUpiUri('upi://pay?pa=alice@ybl');
    expect(result).toEqual({
      payeeAddress: 'alice@ybl',
      payeeName: '',
      amount: '',
      note: '',
      currency: 'INR',
    });
  });

  test('decodes "+" as space in URI components', () => {
    const result = parseUpiUri('upi://pay?pa=shop@upi&pn=Hello+World');
    expect(result.payeeName).toBe('Hello World');
  });

  test('case-insensitive scheme is accepted', () => {
    expect(parseUpiUri('UPI://pay?pa=alice@ybl')).not.toBeNull();
  });

  test('returns null for non-UPI inputs', () => {
    expect(parseUpiUri('https://example.com/?pa=alice@ybl')).toBeNull();
    expect(parseUpiUri('hello world')).toBeNull();
    expect(parseUpiUri('')).toBeNull();
    expect(parseUpiUri(null)).toBeNull();
    expect(parseUpiUri(undefined)).toBeNull();
    expect(parseUpiUri(12345)).toBeNull();
  });

  test('returns null when payeeAddress (pa) is missing', () => {
    expect(parseUpiUri('upi://pay?pn=No%20PA')).toBeNull();
  });

  test('returns null when no query string is present', () => {
    expect(parseUpiUri('upi://pay')).toBeNull();
  });

  test('survives malformed URI components without throwing', () => {
    const result = parseUpiUri('upi://pay?pa=alice@ybl&pn=%E0%A4');
    expect(result).not.toBeNull();
    expect(result.payeeAddress).toBe('alice@ybl');
  });
});
