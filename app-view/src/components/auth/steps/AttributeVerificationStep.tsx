"use client";

import { useEffect, useRef, useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { backendUrl } from "@/pages/_app";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { StepProps } from "./StepProps";

/** One configured input, as the server describes it in `authentication_step_hints`. */
type InputHint = {
  input: string;
  user_attribute: string;
  normalize: string;
  suffix_length: number;
};

const ATTRIBUTE_LABELS: Record<string, string> = {
  birthdate: "Date of birth",
  phone_number: "Phone number",
  email: "Email",
  name: "Full name",
  given_name: "Given name",
  family_name: "Family name",
  "address.postal_code": "Postal code",
};

const labelFor = (hint: InputHint): string => {
  const base =
    ATTRIBUTE_LABELS[hint.user_attribute] ??
    hint.user_attribute.replace(/^custom_properties\./, "");
  return hint.suffix_length > 0
    ? `Last ${hint.suffix_length} digits of your ${base.toLowerCase()}`
    : base;
};

/**
 * Messages for the errors this step answers with. A tenant can choose its own error code for the
 * account conditions; one not listed here falls back to the server's description.
 */
const ERROR_MESSAGES: Record<string, string> = {
  identity_verification_required:
    "Identity verification is required before you can continue. Please complete identity verification and try again.",
  attribute_condition_not_satisfied:
    "Your account does not meet the requirements to continue.",
  attribute_mismatch: "The information you entered does not match our records.",
  too_many_attempts: "Too many attempts. Please try again later.",
};

/** The browser input that suits how the value is compared: a date picker, an email keyboard. */
const inputTypeFor = (hint: InputHint): string => {
  if (hint.normalize === "date") return "date";
  if (hint.normalize === "email") return "email";
  return "text";
};

const readInputs = (hints?: Record<string, unknown>): InputHint[] =>
  Array.isArray(hints?.inputs) ? (hints.inputs as InputHint[]) : [];

/**
 * Attribute verification step (method "attribute-verification", Issue #1907).
 *
 * Each step runs one named interaction (`step.interaction`), described by view-data
 * `authentication_step_hints`. An interaction of kind `conditions` only checks the account
 * (identity-verified, for instance) and asks for nothing, so it is submitted as soon as the step
 * appears. One of kind `fields` asks for whatever the tenant configured — a date of birth, the last
 * digits of a phone number — and submits it to be checked against the account.
 */
export const AttributeVerificationStep = ({
  tenantId,
  id,
  step,
  onCompleted,
}: StepProps) => {
  const inputs = readInputs(step.hints);
  const checksAccount = step.hints?.kind === "conditions";
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const autoSubmitted = useRef(false);

  const submit = async () => {
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/attribute-verification`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...values, interaction: step.interaction }),
        },
      );
      if (!response.ok) {
        setMessage(await errorMessageOf(response));
        return;
      }
      await onCompleted();
    } finally {
      setLoading(false);
    }
  };

  // Nothing to ask for: the step only checks the account, so check it straight away.
  useEffect(() => {
    if (!checksAccount || autoSubmitted.current) return;
    autoSubmitted.current = true;
    void submit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const complete = inputs.every((hint) => (values[hint.input] ?? "") !== "");

  if (!step.interaction || (!checksAccount && inputs.length === 0)) {
    return (
      <Typography color="text.secondary" variant="body2">
        This verification step is not configured.
      </Typography>
    );
  }

  if (checksAccount) {
    return (
      <Stack spacing={3}>
        <Typography variant="body2" color="text.secondary">
          Checking your account…
        </Typography>
        <AuthAlert message={message} />
        {message && (
          <Box display="flex" justifyContent="flex-end">
            <Button
              variant="outlined"
              disabled={loading}
              onClick={submit}
              sx={{ textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} /> : "Check again"}
            </Button>
          </Box>
        )}
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        For your security, please confirm the following.
      </Typography>
      {inputs.map((hint, index) => (
        <TextField
          key={hint.input}
          label={labelFor(hint)}
          type={inputTypeFor(hint)}
          autoFocus={index === 0}
          value={values[hint.input] ?? ""}
          onChange={(e) =>
            setValues((prev) => ({ ...prev, [hint.input]: e.target.value }))
          }
          InputLabelProps={
            hint.normalize === "date" ? { shrink: true } : undefined
          }
          inputProps={
            hint.normalize === "digits"
              ? {
                  inputMode: "numeric",
                  ...(hint.suffix_length > 0
                    ? { maxLength: hint.suffix_length }
                    : {}),
                }
              : undefined
          }
        />
      ))}
      <AuthAlert message={message} />
      <Box display="flex" justifyContent="flex-end">
        <Button
          variant="contained"
          disabled={loading || !complete}
          onClick={submit}
          sx={{ textTransform: "none" }}
        >
          {loading ? <CircularProgress size={24} /> : "Continue"}
        </Button>
      </Box>
    </Stack>
  );
};

const errorMessageOf = async (response: Response): Promise<string> => {
  try {
    const body = await response.json();
    const known = ERROR_MESSAGES[body?.error];
    if (known) return known;
    if (typeof body?.error_description === "string" && body.error_description) {
      return body.error_description;
    }
  } catch {
    // non-JSON body — fall through to the generic message
  }
  return "We couldn't verify your information. Please try again.";
};
