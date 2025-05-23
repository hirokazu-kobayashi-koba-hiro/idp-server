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

package org.idp.server.basic.type.rar;

import java.util.Objects;

/**
 * @see <a
 *     href="https://www.rfc-editor.org/rfc/rfc9396.html#name-authorization-request">Authorization
 *     Details</a>
 */
public class AuthorizationDetailsEntity {
  Object value;

  public AuthorizationDetailsEntity() {}

  public AuthorizationDetailsEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
