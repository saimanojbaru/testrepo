export const formatCurrency = (amount) => {
  const num = Number(amount);
  if (Number.isNaN(num)) return `₹${amount}`;
  return `₹${num.toFixed(2)}`;
};

export const formatTimestamp = (ts) => {
  if (!ts) return '';
  const date = new Date(ts);
  return date.toLocaleString();
};

export const generateId = () => {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};
