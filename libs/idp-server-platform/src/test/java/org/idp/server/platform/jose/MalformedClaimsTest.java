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
package org.idp.server.platform.jose;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * A JWT whose payload is not a claims set is a malformed token, not an internal error: it reaches
 * the server before any authentication, and anyone can send one.
 */
class MalformedClaimsTest {

  /** A compact JWS whose payload is not JSON. The signature is irrelevant to parsing the claims. */
  static final String NOT_JSON_PAYLOAD =
      b64("{\"alg\":\"ES256\",\"typ\":\"JWT\"}")
          + "."
          + b64("not a json object")
          + "."
          + b64("sig");

  static String b64(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void claimsOfAPayloadThatIsNotJsonIsATypedFailure() throws Exception {
    JsonWebSignature jws = JsonWebSignature.parse(NOT_JSON_PAYLOAD);

    assertThrows(JsonWebTokenClaimsInvalidException.class, jws::claims);
  }

  @Test
  void theJoseHandlerReportsItAsAnInvalidJose() {
    JoseHandler handler = new JoseHandler();

    assertThrows(
        JoseInvalidException.class,
        () -> handler.handle(NOT_JSON_PAYLOAD, "{\"keys\":[]}", "", ""));
  }
}
