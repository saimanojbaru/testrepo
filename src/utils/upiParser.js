export const parseUpiUri = (raw) => {
  if (!raw || typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed.toLowerCase().startsWith('upi://')) return null;

  const queryIndex = trimmed.indexOf('?');
  if (queryIndex === -1) return null;

  const query = trimmed.slice(queryIndex + 1);
  const params = {};

  query.split('&').forEach((pair) => {
    if (!pair) return;
    const eqIdx = pair.indexOf('=');
    if (eqIdx === -1) return;
    const key = pair.slice(0, eqIdx);
    const value = pair.slice(eqIdx + 1);
    try {
      params[key] = decodeURIComponent(value.replace(/\+/g, ' '));
    } catch {
      params[key] = value;
    }
  });

  if (!params.pa) return null;

  return {
    payeeAddress: params.pa,
    payeeName: params.pn || '',
    amount: params.am || '',
    note: params.tn || '',
    currency: params.cu || 'INR',
  };
};
