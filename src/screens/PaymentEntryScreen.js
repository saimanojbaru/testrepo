import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import { isValidRecipient, isValidAmount, isUpiId, isMobileNumber } from '../utils/validation';
import { generateId } from '../utils/format';
import GradientButton from '../components/GradientButton';
import { elevation } from '../theme/colors';

const Field = ({ theme, label, error, children, hint }) => (
  <View style={styles.field}>
    <View style={styles.labelRow}>
      <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      {hint ? <Text style={[styles.hint, { color: theme.muted }]}>{hint}</Text> : null}
    </View>
    {children}
    {error ? <Text style={[styles.error, { color: theme.danger }]}>{error}</Text> : null}
  </View>
);

const PaymentEntryScreen = ({ navigation, route }) => {
  const { theme, isOffline, queuePayment } = useNetwork();
  const prefill = route?.params || {};

  const [recipient, setRecipient] = useState(prefill.recipient || '');
  const [amount, setAmount] = useState(prefill.amount ? String(prefill.amount) : '');
  const [note, setNote] = useState(prefill.note || '');
  const [recipientError, setRecipientError] = useState('');
  const [amountError, setAmountError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (prefill.recipient) setRecipient(prefill.recipient);
    if (prefill.amount) setAmount(String(prefill.amount));
    if (prefill.note) setNote(prefill.note);
  }, [prefill.recipient, prefill.amount, prefill.note]);

  const validate = () => {
    let ok = true;
    if (!isValidRecipient(recipient)) {
      setRecipientError('Enter a valid UPI ID (name@bank) or 10-digit Indian mobile number.');
      ok = false;
    } else {
      setRecipientError('');
    }
    if (!isValidAmount(amount)) {
      setAmountError('Enter an amount between ₹0.01 and ₹100,000.');
      ok = false;
    } else {
      setAmountError('');
    }
    return ok;
  };

  const handleSubmit = async () => {
    if (submitting) return;
    if (!validate()) return;
    setSubmitting(true);
    const recipientType = isUpiId(recipient) ? 'upi' : isMobileNumber(recipient) ? 'mobile' : 'unknown';
    const entry = {
      id: generateId(),
      recipient: recipient.trim(),
      recipientType,
      amount: parseFloat(amount),
      note: note.trim(),
      timestamp: Date.now(),
      status: 'pending',
    };
    await queuePayment(entry);
    setSubmitting(false);
    navigation.replace('Success', { entry });
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={[styles.container, { backgroundColor: theme.background }]}
    >
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <Text style={[styles.heading, { color: theme.text }]}>New Payment</Text>
        <Text style={[styles.subheading, { color: theme.subtext }]}>
          {isOffline
            ? 'You are offline. We will queue this intent locally and sync it as soon as your network returns.'
            : 'We capture the payment and queue it locally for a clean sync.'}
        </Text>

        <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }, elevation.small]}>
          <Field theme={theme} label="UPI ID or Mobile" error={recipientError}>
            <TextInput
              testID="recipient-input"
              style={[
                styles.input,
                {
                  backgroundColor: theme.surfaceElevated,
                  borderColor: recipientError ? theme.danger : theme.border,
                  color: theme.text,
                },
              ]}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="email-address"
              placeholder="name@bank or 9876543210"
              placeholderTextColor={theme.muted}
              value={recipient}
              onChangeText={(v) => {
                setRecipient(v);
                if (recipientError) setRecipientError('');
              }}
            />
          </Field>

          <Field theme={theme} label="Amount" hint="₹" error={amountError}>
            <TextInput
              testID="amount-input"
              style={[
                styles.input,
                styles.amountInput,
                {
                  backgroundColor: theme.surfaceElevated,
                  borderColor: amountError ? theme.danger : theme.border,
                  color: theme.text,
                },
              ]}
              keyboardType="decimal-pad"
              placeholder="0.00"
              placeholderTextColor={theme.muted}
              value={amount}
              onChangeText={(v) => {
                setAmount(v.replace(/[^0-9.]/g, ''));
                if (amountError) setAmountError('');
              }}
            />
          </Field>

          <Field theme={theme} label="Note" hint="Optional">
            <TextInput
              testID="note-input"
              style={[styles.input, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, color: theme.text }]}
              placeholder="What's this for?"
              placeholderTextColor={theme.muted}
              value={note}
              onChangeText={setNote}
            />
          </Field>
        </View>

        <GradientButton
          colors={theme.primaryGradient}
          label="Save Offline"
          sublabel={isOffline ? 'Queued until network returns' : 'Queued and synced shortly'}
          onPress={handleSubmit}
          disabled={submitting}
          size="lg"
          style={styles.submit}
        />

        <Text style={[styles.helper, { color: theme.muted }]}>
          No money is moved right now. We save the intent locally and complete it automatically once your internet returns.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 60 },
  heading: { fontSize: 26, fontWeight: '800', marginBottom: 4, letterSpacing: -0.4 },
  subheading: { fontSize: 13, marginBottom: 22, lineHeight: 19 },
  card: {
    borderRadius: 18,
    borderWidth: StyleSheet.hairlineWidth,
    padding: 18,
    marginBottom: 22,
  },
  field: { marginBottom: 14 },
  labelRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  label: { fontSize: 12, fontWeight: '700', letterSpacing: 0.5, textTransform: 'uppercase' },
  hint: { fontSize: 11, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 13,
    paddingHorizontal: 14,
    fontSize: 16,
  },
  amountInput: { fontSize: 22, fontWeight: '700', letterSpacing: -0.4 },
  error: { fontSize: 12, marginTop: 6, fontWeight: '600' },
  submit: { marginTop: 4 },
  helper: { fontSize: 12, marginTop: 18, textAlign: 'center', lineHeight: 17 },
});

export default PaymentEntryScreen;
