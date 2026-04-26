import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';

import { useNetwork } from '../context/NetworkContext';
import { isValidRecipient, isValidAmount, isUpiId, isMobileNumber } from '../utils/validation';
import { generateId } from '../utils/format';

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
      setRecipientError('Enter a valid UPI ID (e.g. name@bank) or 10-digit Indian mobile number.');
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

  const buttonLabel = isOffline ? 'Save Offline' : 'Save Offline';

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={[styles.container, { backgroundColor: theme.background }]}
    >
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={[styles.heading, { color: theme.text }]}>Enter Payment Details</Text>
        <Text style={[styles.subheading, { color: theme.subtext }]}>
          {isOffline
            ? 'You are offline. This payment intent will be queued locally and processed automatically once you are back online.'
            : 'Capture this payment now. It will be queued locally and synced.'}
        </Text>

        <Text style={[styles.label, { color: theme.text }]}>UPI ID or Mobile Number</Text>
        <TextInput
          style={[
            styles.input,
            {
              backgroundColor: theme.surface,
              borderColor: recipientError ? theme.danger : theme.border,
              color: theme.text,
            },
          ]}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="email-address"
          placeholder="name@bank or 9876543210"
          placeholderTextColor={theme.subtext}
          value={recipient}
          onChangeText={(v) => {
            setRecipient(v);
            if (recipientError) setRecipientError('');
          }}
        />
        {recipientError ? <Text style={[styles.error, { color: theme.danger }]}>{recipientError}</Text> : null}

        <Text style={[styles.label, { color: theme.text }]}>Amount (₹)</Text>
        <TextInput
          style={[
            styles.input,
            {
              backgroundColor: theme.surface,
              borderColor: amountError ? theme.danger : theme.border,
              color: theme.text,
            },
          ]}
          keyboardType="decimal-pad"
          placeholder="0.00"
          placeholderTextColor={theme.subtext}
          value={amount}
          onChangeText={(v) => {
            setAmount(v.replace(/[^0-9.]/g, ''));
            if (amountError) setAmountError('');
          }}
        />
        {amountError ? <Text style={[styles.error, { color: theme.danger }]}>{amountError}</Text> : null}

        <Text style={[styles.label, { color: theme.text }]}>Note (optional)</Text>
        <TextInput
          style={[styles.input, { backgroundColor: theme.surface, borderColor: theme.border, color: theme.text }]}
          placeholder="What's this for?"
          placeholderTextColor={theme.subtext}
          value={note}
          onChangeText={setNote}
        />

        <TouchableOpacity
          style={[styles.submit, { backgroundColor: theme.primary, opacity: submitting ? 0.6 : 1 }]}
          onPress={handleSubmit}
          disabled={submitting}
          activeOpacity={0.85}
        >
          <Text style={[styles.submitText, { color: theme.primaryText }]}>{buttonLabel}</Text>
        </TouchableOpacity>

        <Text style={[styles.helper, { color: theme.subtext }]}>
          No money is moved right now. We save the intent locally and complete it automatically once your internet returns.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, paddingBottom: 60 },
  heading: { fontSize: 22, fontWeight: '800', marginBottom: 4 },
  subheading: { fontSize: 13, marginBottom: 20, lineHeight: 18 },
  label: { fontSize: 13, fontWeight: '600', marginBottom: 6, marginTop: 12 },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 14,
    fontSize: 16,
  },
  error: { fontSize: 12, marginTop: 4 },
  submit: {
    marginTop: 28,
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  submitText: { fontSize: 16, fontWeight: '700' },
  helper: { fontSize: 12, marginTop: 16, textAlign: 'center', lineHeight: 16 },
});

export default PaymentEntryScreen;
