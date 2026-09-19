/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.identity.contact;

/**
 * Partial masking for a contact value quoted back in a change notice (Issue #1416).
 *
 * <p>The notice goes to the value being replaced, which after a takeover is the inbox the rightful
 * owner still reads — so it must say enough for them to tell "that is not mine", while not handing
 * a complete address or number to whoever might also be reading it.
 */
public class ContactValueMask {

  /** Keeps the first character and the domain for an email, the last four digits for a phone. */
  public static String of(ContactChannel channel, String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return channel.isEmail() ? maskEmail(value) : maskPhone(value);
  }

  private static String maskEmail(String value) {
    int at = value.lastIndexOf('@');
    if (at <= 0) {
      return maskTail(value);
    }
    String local = value.substring(0, at);
    String domain = value.substring(at);
    return local.charAt(0) + "***" + domain;
  }

  private static String maskPhone(String value) {
    String digits = value.replaceAll("\\D", "");
    if (digits.length() <= 4) {
      return "****";
    }
    return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
  }

  private static String maskTail(String value) {
    return value.length() <= 2 ? "***" : value.charAt(0) + "***";
  }
}
