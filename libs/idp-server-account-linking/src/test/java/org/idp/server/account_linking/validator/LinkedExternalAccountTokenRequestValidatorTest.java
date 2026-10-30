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

package org.idp.server.account_linking.validator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenParameters;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountTokenRequestValidatorTest {

  LinkedExternalAccountTokenRequestValidator validator =
      new LinkedExternalAccountTokenRequestValidator();

  static LinkedExternalAccountTokenParameters parameters(String forceRefresh) {
    Map<String, String[]> values = new HashMap<>();
    values.put("token", new String[] {"user-access-token"});
    if (forceRefresh != null) {
      values.put("force_refresh", new String[] {forceRefresh});
    }
    return new LinkedExternalAccountTokenParameters(values);
  }

  @Test
  void forcesARefreshOnlyWhenAskedWithTrue() {
    assertTrue(parameters("true").forceRefresh());
    assertFalse(parameters("false").forceRefresh());
    assertFalse(parameters(null).forceRefresh());
  }

  @Test
  void acceptsForceRefreshAbsentTrueOrFalse() {
    assertDoesNotThrow(() -> validator.validate(parameters(null)));
    assertDoesNotThrow(() -> validator.validate(parameters("true")));
    assertDoesNotThrow(() -> validator.validate(parameters("false")));
  }

  @Test
  void refusesAnyOtherForceRefresh() {
    for (String value : new String[] {"1", "TRUE", "yes", ""}) {
      LinkedExternalAccountTokenException exception =
          assertThrows(
              LinkedExternalAccountTokenException.class,
              () -> validator.validate(parameters(value)),
              value);
      assertEquals("invalid_request", exception.error());
    }
  }
}
