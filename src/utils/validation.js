const UPI_REGEX = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/;
const MOBILE_REGEX = /^[6-9]\d{9}$/;
const AMOUNT_REGEX = /^\d+(\.\d{1,2})?$/;

export const isUpiId = (value) => UPI_REGEX.test(String(value || '').trim());

export const isMobileNumber = (value) => MOBILE_REGEX.test(String(value || '').replace(/\s+/g, ''));

export const isValidRecipient = (value) => isUpiId(value) || isMobileNumber(value);

export const isValidAmount = (value) => {
  const v = String(value || '').trim();
  if (!AMOUNT_REGEX.test(v)) return false;
  const num = parseFloat(v);
  return num > 0 && num <= 100000;
};
