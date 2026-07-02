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

package org.idp.server.authentication.interactors.device;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NumberMatchingCodeGeneratorTest {

  @Test
  void generatesDigitsOnly() {
    for (int i = 0; i < 500; i++) {
      String code = NumberMatchingCodeGenerator.generate(4);
      assertEquals(4, code.length());
      assertTrue(code.matches("[0-9]{4}"), "unexpected characters: " + code);
    }
  }

  @Test
  void honorsLength() {
    assertEquals(6, NumberMatchingCodeGenerator.generate(6).length());
  }

  @Test
  void rejectsNonPositiveLength() {
    assertThrows(IllegalArgumentException.class, () -> NumberMatchingCodeGenerator.generate(0));
    assertThrows(IllegalArgumentException.class, () -> NumberMatchingCodeGenerator.generate(-1));
  }
}
