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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RandomStringFunctionTest {

  private final RandomStringFunction function = new RandomStringFunction();

  @Test
  public void testName() {
    assertEquals("random_string", function.name());
  }

  @Test
  public void testApplyWithDefaultParameters() {
    Map<String, Object> args = new HashMap<>();

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(16, result.length()); // default length
    assertTrue(result.matches("[A-Za-z0-9]+"));
  }

  @Test
  public void testApplyWithCustomLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 30);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(30, result.length());
    assertTrue(result.matches("[A-Za-z0-9]+"));
  }

  @Test
  public void testApplyWithCustomCharset() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 20);
    args.put("charset", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(20, result.length());
    assertTrue(result.matches("[A-Z0-9]+"));
  }

  @Test
  public void testApplyWithMinimalCharset() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 10);
    args.put("charset", "ABC");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(10, result.length());
    assertTrue(result.matches("[ABC]+"));
  }

  @Test
  public void testApplyWithZeroLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 0);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  @Test
  public void testApplyGeneratesUniqueStrings() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 32);

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertEquals(32, result1.length());
    assertEquals(32, result2.length());
    assertNotEquals(result1, result2); // Should be different (statistically almost certain)
  }

  @Test
  public void testIssue388Example() {
    // Test the exact example from Issue #388
    Map<String, Object> args = new HashMap<>();
    args.put("length", 30);
    args.put("charset", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(30, result.length());
    assertTrue(result.matches("[A-Z0-9]+"));

    // Verify all characters are from the specified charset
    for (char c : result.toCharArray()) {
      assertTrue("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".indexOf(c) >= 0);
    }
  }
}
