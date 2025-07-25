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

package org.idp.server.platform.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** HashAlgorithm */
enum HashAlgorithm {
  MD5("MD5"),
  SHA_1("SHA-1"),
  SHA_256("SHA-256"),
  SHA_384("SHA-384"),
  SHA_512("SHA-512");

  String value;
  MessageDigest messageDigest;

  HashAlgorithm(String value) {
    this.value = value;
    try {
      this.messageDigest = MessageDigest.getInstance(value);
    } catch (NoSuchAlgorithmException exception) {
      throw new RuntimeException(exception);
    }
  }

  public String value() {
    return value;
  }

  public MessageDigest messageDigest() {
    return messageDigest;
  }
}
