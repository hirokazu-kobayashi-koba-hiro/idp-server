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

package org.idp.server.core.openid.oauth.clientattestation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * The subject is read before the client is authenticated, to select which client to load. Whatever
 * arrives in the header, reading it must not fail: an unreadable one selects no client.
 */
class ClientAttestationJwtTest {

  static String b64(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aPayloadThatIsNotJsonHasNoSubject() {
    String notJson =
        b64("{\"alg\":\"ES256\",\"typ\":\"oauth-client-attestation+jwt\"}")
            + "."
            + b64("not a json object")
            + "."
            + b64("sig");

    assertEquals("", new ClientAttestationJwt(notJson).extractSubject());
  }

  @Test
  void aValueThatIsNotAJwtHasNoSubject() {
    assertEquals("", new ClientAttestationJwt("garbage").extractSubject());
  }
}
