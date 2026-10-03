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

package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class DateFunctionTest {

  private final DateFunction function = new DateFunction();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1990-04-01",
        "1990-4-1",
        "1990/04/01",
        "1990/4/1",
        "1990.4.1",
        "19900401",
        "1990年4月1日",
        "1990年04月01日",
        "１９９０／０４／０１",
        "１９９０年４月１日",
        " 1990-04-01 "
      })
  void commonNotationsAreReadAsTheSameDate(String input) {
    assertEquals("1990-04-01", function.apply(input, Map.of()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1990-02-30", "H2.4.1", "平成2年4月1日", "1990-04", "not a date", ""})
  void unreadableInputYieldsNull(String input) {
    assertNull(function.apply(input, Map.of()));
  }

  @Test
  void outputFormatCanBeChosen() {
    assertEquals("1990/04/01", function.apply("19900401", Map.of("format", "uuuu/MM/dd")));
  }

  @Test
  void nullInput() {
    assertNull(function.apply(null, Map.of()));
  }

  @Test
  void invalidFormatIsAnError() {
    assertThrows(
        IllegalArgumentException.class,
        () -> function.apply("1990-04-01", Map.of("format", "uuuu-MM-dd'")));
  }
}
