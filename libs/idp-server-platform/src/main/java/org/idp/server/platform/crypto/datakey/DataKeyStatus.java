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
package org.idp.server.platform.crypto.datakey;

/** The life of a data key. */
public enum DataKeyStatus {
  /** Encrypts new data. One per tenant and purpose. */
  ACTIVE,
  /** Only opens what it encrypted before a newer generation took over. */
  PASSIVE,
  /** Opens nothing any more. */
  DESTROYED;

  public static DataKeyStatus of(String value) {
    return valueOf(value.toUpperCase());
  }

  public String value() {
    return name().toLowerCase();
  }
}
