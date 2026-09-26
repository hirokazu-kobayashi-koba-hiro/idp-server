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
package org.idp.server.platform.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServerNonceCodecTest {

  static final String TENANT = "67e7eae6-62b0-4500-9eff-87459f63fc66";
  static final String OTHER_TENANT = "b01c0787-b7be-4699-9a5a-042f70d41697";
  static final long EXP = 1_790_300_000L;

  ServerNonceCodec codec = new ServerNonceCodec("server-secret-for-tests");

  @Test
  @DisplayName("発行した値は、同じ用途・テナントで照合でき、有効期限が返る")
  void issuedValueVerifies() {
    String value = codec.issue("challenge", TENANT, EXP);

    assertEquals(EXP, codec.verify("challenge", TENANT, value));
    assertTrue(value.length() < 110, value);
  }

  @Test
  @DisplayName("用途・テナント・鍵が違えば照合できない")
  void valueIsBoundToPurposeTenantAndSecret() {
    String value = codec.issue("challenge", TENANT, EXP);

    assertNull(codec.verify("c_nonce", TENANT, value));
    assertNull(codec.verify("challenge", OTHER_TENANT, value));
    assertNull(new ServerNonceCodec("another-secret").verify("challenge", TENANT, value));
  }

  @Test
  @DisplayName("中身を書き換えたもの・形の崩れたものは照合できない")
  void tamperedOrMalformedValuesDoNotVerify() {
    String value = codec.issue("challenge", TENANT, EXP);
    String payload = value.substring(0, value.indexOf('.'));
    String mac = value.substring(value.indexOf('.') + 1);
    String otherPayload = codec.issue("challenge", TENANT, EXP + 3600).split("\\.")[0];

    assertNull(codec.verify("challenge", TENANT, otherPayload + "." + mac));
    assertNull(codec.verify("challenge", TENANT, payload + "." + mac.substring(1)));
    assertNull(codec.verify("challenge", TENANT, payload));
    assertNull(codec.verify("challenge", TENANT, value + ".x"));
    assertNull(codec.verify("challenge", TENANT, ""));
    assertNull(codec.verify("challenge", TENANT, null));
  }

  @Test
  @DisplayName("値は毎回違う（予測できない）")
  void valuesAreUnpredictable() {
    Set<String> values = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      values.add(codec.issue("challenge", TENANT, EXP));
    }
    assertEquals(100, values.size());
  }
}
