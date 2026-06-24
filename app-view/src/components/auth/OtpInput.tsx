"use client";

import {
  ChangeEvent,
  ClipboardEvent,
  KeyboardEvent,
  useRef,
} from "react";
import { Stack, TextField } from "@mui/material";

type Props = {
  value: string;
  onChange: (value: string) => void;
  length?: number;
  autoFocus?: boolean;
  disabled?: boolean;
};

/**
 * Segmented one-time-code input: one box per digit with auto-advance, backspace-to-previous,
 * arrow navigation and full paste support. The value is the joined string (e.g. "123456").
 */
export const OtpInput = ({
  value,
  onChange,
  length = 6,
  autoFocus,
  disabled,
}: Props) => {
  const refs = useRef<Array<HTMLInputElement | null>>([]);

  const setChar = (index: number, char: string) => {
    const chars = value.split("");
    chars[index] = char;
    onChange(chars.join("").slice(0, length));
  };

  const handleChange =
    (index: number) => (event: ChangeEvent<HTMLInputElement>) => {
      const digit = event.target.value.replace(/\D/g, "").slice(-1);
      if (!digit) return;
      setChar(index, digit);
      if (index < length - 1) refs.current[index + 1]?.focus();
    };

  const handleKeyDown =
    (index: number) => (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "Backspace") {
        event.preventDefault();
        if (value[index]) {
          setChar(index, "");
        } else if (index > 0) {
          setChar(index - 1, "");
          refs.current[index - 1]?.focus();
        }
      } else if (event.key === "ArrowLeft" && index > 0) {
        refs.current[index - 1]?.focus();
      } else if (event.key === "ArrowRight" && index < length - 1) {
        refs.current[index + 1]?.focus();
      }
    };

  const handlePaste = (event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    const digits = event.clipboardData
      .getData("text")
      .replace(/\D/g, "")
      .slice(0, length);
    if (!digits) return;
    onChange(digits);
    refs.current[Math.min(digits.length, length - 1)]?.focus();
  };

  return (
    <Stack
      direction="row"
      spacing={1}
      justifyContent="center"
      onPaste={handlePaste}
    >
      {Array.from({ length }).map((_, index) => (
        <TextField
          key={index}
          inputRef={(el: HTMLInputElement | null) => {
            refs.current[index] = el;
          }}
          value={value[index] ?? ""}
          onChange={handleChange(index)}
          onKeyDown={handleKeyDown(index)}
          autoFocus={autoFocus && index === 0}
          disabled={disabled}
          inputProps={{
            inputMode: "numeric",
            maxLength: 1,
            "aria-label": `Digit ${index + 1}`,
            autoComplete: index === 0 ? "one-time-code" : "off",
            style: {
              textAlign: "center",
              fontSize: "1.25rem",
              padding: "10px 0",
            },
          }}
          sx={{ width: 44 }}
        />
      ))}
    </Stack>
  );
};
