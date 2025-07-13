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

package org.idp.server.platform.random;

import java.security.SecureRandom;
import org.idp.server.platform.base64.Base64Codeable;

/** RandomStringGenerator */
public class RandomStringGenerator implements Base64Codeable {

  SecureRandom secureRandom;
  byte[] bytes;

  public RandomStringGenerator(int keyLength) {
    this.secureRandom = new SecureRandom();
    this.bytes = new byte[keyLength];
    secureRandom.nextBytes(bytes);
  }

  public String generate() {
    return encodeWithUrlSafe(bytes);
  }
}
