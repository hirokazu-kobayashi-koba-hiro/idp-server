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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.junit.jupiter.api.Test;

public class Base64FunctionTest {

  private final Base64Function function = new Base64Function();

  private static Map<String, Object> args(Object... keyValues) {
    Map<String, Object> args = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      args.put((String) keyValues[i], keyValues[i + 1]);
    }
    return args;
  }

  @Test
  public void testName() {
    assertEquals("base64", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithNullArgsUsesDefaults() {
    // The client_secret_basic case: raw credentials in, encoded header value out.
    assertEquals("aWQ6c2VjcmV0", function.apply("id:secret", null));
  }

  @Test
  public void testApplyWithEmptyArgsUsesDefaults() {
    assertEquals("aWQ6c2VjcmV0", function.apply("id:secret", Map.of()));
  }

  @Test
  public void testApplyWithEmptyStringInput() {
    assertEquals("", function.apply("", null));
  }

  @Test
  public void testPaddingIsEmittedByDefault() {
    assertEquals("YQ==", function.apply("a", null));
  }

  @Test
  public void testPaddingCanBeDisabled() {
    assertEquals("YQ", function.apply("a", args("padding", false)));
  }

  @Test
  public void testUrlSafeAlphabet() {
    // UTF-8 bytes C3 BF C3 BE encode to "w7/Dvg==" with the standard alphabet.
    assertEquals("w7/Dvg==", function.apply("ÿþ", null));
    assertEquals("w7_Dvg==", function.apply("ÿþ", args("url_safe", true)));
  }

  @Test
  public void testBase64UrlIsUrlSafeWithoutPadding() {
    String result = (String) function.apply("ÿþ", args("url_safe", true, "padding", false));

    assertEquals("w7_Dvg", result);
    assertFalse(result.contains("+"));
    assertFalse(result.contains("/"));
    assertFalse(result.contains("="));
  }

  @Test
  public void testCharsetDefaultsToUtf8() {
    assertEquals(function.apply("é", args("charset", "UTF-8")), function.apply("é", null));
  }

  @Test
  public void testCharsetIsHonored() {
    // "é" is two bytes in UTF-8 (C3 A9) but one in ISO-8859-1 (E9).
    assertEquals("w6k=", function.apply("é", args("charset", "UTF-8")));
    assertEquals("6Q==", function.apply("é", args("charset", "ISO-8859-1")));
  }

  @Test
  public void testUnsupportedCharsetIsRejected() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> function.apply("id:secret", args("charset", "NOT-A-CHARSET")));

    assertTrue(exception.getMessage().contains("NOT-A-CHARSET"));
  }

  @Test
  public void testBooleanArgsAcceptStringValues() {
    // Config JSON may carry "true"/"false" as strings depending on how it was authored.
    assertEquals("YQ", function.apply("a", args("padding", "false")));
    assertEquals("w7_Dvg==", function.apply("ÿþ", args("url_safe", "true")));
    assertEquals("YQ", function.apply("a", args("padding", "FALSE")));
  }

  @Test
  public void testNullBooleanArgFallsBackToDefault() {
    assertEquals("YQ==", function.apply("a", args("padding", null)));
  }

  @Test
  public void testUnusableBooleanArgIsRejected() {
    // Boolean.parseBoolean would read "yes" as false, silently inverting the default. A charset
    // typo already fails fast; a flag typo should not be treated more leniently.
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> function.apply("a", args("padding", "yes")));

    assertTrue(exception.getMessage().contains("padding"));
    assertTrue(exception.getMessage().contains("yes"));

    assertThrows(IllegalArgumentException.class, () -> function.apply("a", args("url_safe", 1)));
  }

  @Test
  public void testNonStringInputIsStringified() {
    assertEquals("MTIz", function.apply(123, null));
  }

  @Test
  public void testRoundTripsWithJavaDecoder() {
    String input = "user@example.com:p@ssw0rd/with+special=chars";

    String standard = (String) function.apply(input, null);
    assertEquals(input, new String(Base64.getDecoder().decode(standard), StandardCharsets.UTF_8));

    String urlSafe = (String) function.apply(input, args("url_safe", true, "padding", false));
    assertEquals(input, new String(Base64.getUrlDecoder().decode(urlSafe), StandardCharsets.UTF_8));
  }

  @Test
  public void testRegisteredInFunctionRegistry() {
    FunctionRegistry registry = new FunctionRegistry();

    assertTrue(registry.exists("base64"));
    assertEquals("aWQ6c2VjcmV0", registry.get("base64").apply("id:secret", null));
  }

  @Test
  public void testBuildsBasicAuthorizationHeaderFromDocumentedConfiguration() {
    // The documented client_secret_basic recipe, parsed as configuration JSON rather than
    // hand-built, so the snake_case binding and the function chain are both covered:
    // static_value feeds the chain the same way `from` does, and the header is derived at
    // request time instead of being stored pre-encoded.
    String configJson =
        """
        {
          "static_value": "<client_id>:<client_secret>",
          "to": "Authorization",
          "functions": [
            { "name": "base64" },
            { "name": "format", "args": { "template": "Basic {{value}}" } }
          ]
        }
        """;
    MappingRule rule = JsonConverter.snakeCaseInstance().read(configJson, MappingRule.class);

    Map<String, Object> result =
        MappingRuleObjectMapper.execute(List.of(rule), new JsonPathWrapper("{}"));

    assertEquals(
        "Basic " + Base64.getEncoder().encodeToString("<client_id>:<client_secret>".getBytes()),
        result.get("Authorization"));
  }
}
