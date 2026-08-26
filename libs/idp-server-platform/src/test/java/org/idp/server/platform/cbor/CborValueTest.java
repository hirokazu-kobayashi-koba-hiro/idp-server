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

package org.idp.server.platform.cbor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Reading CBOR through the wrapper.
 *
 * <p>The structure exercised here is the shape of an Apple App Attest attestation object, since
 * that is what the wrapper exists for: a map holding a text string, a nested map, and byte strings
 * both directly and inside an array.
 */
class CborValueTest {

  static final CBORMapper MAPPER = CBORMapper.builder().build();

  static byte[] attestationObject() {
    return MAPPER.writeValueAsBytes(
        Map.of(
            "fmt",
            "apple-appattest",
            "attStmt",
            Map.of(
                "x5c",
                List.of(new byte[] {0x30, 0x01}, new byte[] {0x30, 0x02}),
                "receipt",
                new byte[] {0x30, 0x03}),
            "authData",
            new byte[] {0x21, (byte) 0xc9}));
  }

  @Nested
  class Reads {

    @Test
    void readsTheAttestationObjectShape() throws Exception {
      CborValue value = CborValue.parse(attestationObject());

      assertEquals("apple-appattest", value.get("fmt").textString());
      assertArrayEquals(new byte[] {0x21, (byte) 0xc9}, value.get("authData").byteString());

      List<CborValue> x5c = value.get("attStmt").get("x5c").elements();
      assertEquals(2, x5c.size());
      assertArrayEquals(new byte[] {0x30, 0x01}, x5c.get(0).byteString());
    }

    @Test
    void reportsWhetherAKeyIsPresent() throws Exception {
      CborValue value = CborValue.parse(attestationObject());

      assertTrue(value.contains("authData"));
      assertFalse(value.contains("assertion"));
    }
  }

  @Nested
  class StrictReads {

    @Test
    void rejectsAMissingKey() throws Exception {
      CborValue value = CborValue.parse(attestationObject());

      assertThrows(CborInvalidException.class, () -> value.get("absent"));
    }

    @Test
    void rejectsATextStringReadAsAByteString() throws Exception {
      // CBOR distinguishes the two. Coercing here would accept evidence encoded the wrong way,
      // and every downstream comparison would then run against bytes the sender chose freely.
      CborValue value = CborValue.parse(attestationObject());

      assertThrows(CborInvalidException.class, () -> value.get("fmt").byteString());
    }

    @Test
    void rejectsAMapReadAsAnArray() throws Exception {
      CborValue value = CborValue.parse(attestationObject());

      assertThrows(CborInvalidException.class, () -> value.get("attStmt").elements());
    }

    @Test
    void rejectsBytesThatAreNotCbor() {
      assertThrows(
          CborInvalidException.class, () -> CborValue.parse(new byte[] {(byte) 0xff, 0x00}));
    }
  }
}
