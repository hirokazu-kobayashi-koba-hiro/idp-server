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

package org.idp.server.platform.base64;

import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;

/** Base64Codeable */
public interface Base64Codeable {

  default byte[] encode(byte[] input) {
    return Base64.encode(input).toString().getBytes();
  }

  default String encodeString(byte[] input) {
    return Base64.encode(input).toString();
  }

  default String encodeWithUrlSafe(String input) {
    return Base64URL.encode(input).toString();
  }

  default String encodeWithUrlSafe(byte[] input) {
    return Base64URL.encode(input).toString();
  }

  default String decodeWithUrlSafe(String input) {
    return Base64URL.from(input).decodeToString();
  }
}
