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

package org.idp.server.basic.type.oauth;

import java.util.Objects;

/**
 * error_description OPTIONAL.
 *
 * <p>Human-readable ASCII [USASCII] text providing additional information, used to assist the
 * client developer in understanding the error that occurred. Values for the "error_description"
 * parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
 */
public class ErrorDescription {
  String value;

  public ErrorDescription() {}

  public ErrorDescription(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ErrorDescription that = (ErrorDescription) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
