import { formatCurrency, formatTimestamp, generateId } from '../format';

describe('formatCurrency', () => {
  test('formats integer rupee amounts with 2 decimals', () => {
    expect(formatCurrency(100)).toBe('₹100.00');
    expect(formatCurrency('250')).toBe('₹250.00');
  });

  test('rounds to 2 decimal places', () => {
    expect(formatCurrency(199.5)).toBe('₹199.50');
    expect(formatCurrency('1.999')).toBe('₹2.00');
  });

  test('falls back to raw value for non-numeric', () => {
    expect(formatCurrency('abc')).toBe('₹abc');
  });
});

describe('formatTimestamp', () => {
  test('returns empty string for falsy input', () => {
    expect(formatTimestamp(0)).toBe('');
    expect(formatTimestamp(null)).toBe('');
    expect(formatTimestamp(undefined)).toBe('');
  });

  test('returns a non-empty string for a real timestamp', () => {
    const result = formatTimestamp(Date.now());
    expect(typeof result).toBe('string');
    expect(result.length).toBeGreaterThan(0);
  });
});

describe('generateId', () => {
  test('produces unique-looking ids on consecutive calls', () => {
    const ids = new Set();
    for (let i = 0; i < 50; i += 1) {
      ids.add(generateId());
    }
    expect(ids.size).toBe(50);
  });

  test('contains a timestamp prefix', () => {
    const id = generateId();
    expect(id).toMatch(/^\d+-[a-z0-9]+$/);
  });
});
