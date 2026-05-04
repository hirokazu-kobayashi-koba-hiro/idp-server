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

package org.idp.server.core.openid.oauth.verifier.rule;

import java.util.Objects;

/**
 * {@link Rule} の安定識別子。
 *
 * <p>慣習: {@code "<profile>.<endpoint>.<concern>"} (例: {@code "fapi2.par.response-type-code"})
 *
 * <p>ログ / メトリクス / テスト assertion から rule を特定するために使う。文字列を直接扱うのを避け、誤字を防ぐ。
 */
public final class RuleId {
  private final String value;

  public RuleId(String value) {
    Objects.requireNonNull(value, "RuleId value must not be null");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("RuleId value must not be empty");
    }
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RuleId)) return false;
    return value.equals(((RuleId) o).value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
