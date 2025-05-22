/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.base64;

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
