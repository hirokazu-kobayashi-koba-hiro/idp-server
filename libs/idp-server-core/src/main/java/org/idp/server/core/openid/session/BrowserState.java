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

package org.idp.server.core.openid.session;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class BrowserState {

  private static final int RANDOM_BYTES_LENGTH = 32;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final String value;

  public BrowserState() {
    this.value = "";
  }

  public BrowserState(String value) {
    this.value = value;
  }

  public static BrowserState generate() {
    byte[] randomBytes = new byte[RANDOM_BYTES_LENGTH];
    SECURE_RANDOM.nextBytes(randomBytes);
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    return new BrowserState(encoded);
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BrowserState that = (BrowserState) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
