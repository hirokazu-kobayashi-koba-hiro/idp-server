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

import java.util.Objects;

/**
 * Server-assigned identifier for one linked external account, in the form {@code {provider}-{seq}}.
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

  /** Builds the alias for the {@code sequence}-th account linked to {@code provider}. */
  public static AccountAlias of(ExternalIdpProvider provider, int sequence) {
    return new AccountAlias(provider.value() + "-" + sequence);
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
