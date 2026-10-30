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
 * Opaque handle for a linking session.
 *
 * <p>Doubles as the OAuth {@code state} sent to the external IdP. Its secrecy is deliberately not
 * load bearing: possession alone finalizes nothing, because both {@code /linking/start} and {@code
 * complete} verify the operator against the user bound at link time.
 */
public class AccountLinkingState {

  String value;

  public AccountLinkingState() {}

  public AccountLinkingState(String value) {
    this.value = value;
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
    AccountLinkingState that = (AccountLinkingState) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
