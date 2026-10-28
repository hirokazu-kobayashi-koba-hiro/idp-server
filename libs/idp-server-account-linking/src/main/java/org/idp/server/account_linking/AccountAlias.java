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

package org.idp.server.account_linking;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Server-assigned identifier for one linked external account, in the form {@code
 * {provider}-{random}} (e.g. {@code google-k3f9x2qa}).
 *
 * <p>The suffix is random rather than a sequence. A sequence is reused once the links that held its
 * numbers are removed, and an RP that kept an alias would then be handed the tokens of another
 * external account under the name it stored. Random suffixes are never reused, and two links
 * completed at the same time do not race for the same number.
 *
 * <p>Assigned at completion rather than at link start, because the external account the user will
 * consent with is not known when the flow begins. Never derived from the display name, which the
 * external IdP can change out from under a unique constraint.
 */
public class AccountAlias {

  String value;

  public AccountAlias() {}

  public AccountAlias(String value) {
    this.value = value;
  }

  static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  static final int SUFFIX_LENGTH = 8;
  static final SecureRandom RANDOM = new SecureRandom();

  /** A new alias for an account linked to {@code provider}. */
  public static AccountAlias generate(ExternalIdpProvider provider) {
    StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
    for (int i = 0; i < SUFFIX_LENGTH; i++) {
      suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return new AccountAlias(provider.value() + "-" + suffix);
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AccountAlias that = (AccountAlias) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
