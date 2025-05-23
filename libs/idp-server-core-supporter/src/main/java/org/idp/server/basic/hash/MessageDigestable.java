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

package org.idp.server.basic.hash;

import java.security.MessageDigest;

public interface MessageDigestable {

  default byte[] digestWithMD5(String value) {
    MessageDigest messageDigest = HashAlgorithm.MD5.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha1(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_1.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha256(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_256.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha384(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_384.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithSha512(String value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_512.messageDigest();
    return messageDigest.digest(value.getBytes());
  }

  default byte[] digestWithMD5(byte[] value) {
    MessageDigest messageDigest = HashAlgorithm.MD5.messageDigest();
    return messageDigest.digest(value);
  }

  default byte[] digestWithSha1(byte[] value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_1.messageDigest();
    return messageDigest.digest(value);
  }

  default byte[] digestWithSha256(byte[] value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_256.messageDigest();
    return messageDigest.digest(value);
  }

  default byte[] digestWithSha384(byte[] value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_384.messageDigest();
    return messageDigest.digest(value);
  }

  default byte[] digestWithSha512(byte[] value) {
    MessageDigest messageDigest = HashAlgorithm.SHA_512.messageDigest();
    return messageDigest.digest(value);
  }
}
