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

package org.idp.server.core.openid.oauth.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientExtensionConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * #1762: the management update replaces the whole configuration, so {@code toMap()} — the
 * representation the management GET returns — has to carry every field the update can read back. A
 * field that is missing here is silently dropped by a GET -&gt; modify -&gt; PUT round trip, and
 * the diff cannot show it either because the diff is calculated over the same representation.
 *
 * <p>This is a ledger as much as a test: every field that is knowingly left out has to be listed
 * below with the reason. Adding a field to a configuration without exposing it fails here rather
 * than in production.
 */
class ManagementRepresentationCompletenessTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  /**
   * The verifiable credential model is provisional and does not hold every member of the stored
   * metadata, so exposing it would advertise a round trip that drops what the model does not know.
   */
  private static final Set<String> AUTHORIZATION_SERVER_EXCLUSIONS =
      Set.of("credential_issuer_metadata");

  /**
   * Timestamps are assigned by the server on write, so they are not part of what a caller sends
   * back. Dropping them from the update payload is intended.
   */
  private static final Set<String> CLIENT_EXCLUSIONS = Set.of("created_at", "updated_at");

  /**
   * A federation entry is a model of its own rather than a plain value, so this test cannot build a
   * sample for it. {@code toMap()} does carry it; it is listed here because the sample cannot.
   */
  private static final Set<String> CLIENT_EXTENSION_EXCLUSIONS = Set.of("available_federations");

  @Test
  void authorizationServerRepresentationCarriesEveryField() {
    assertRoundTrippable(AuthorizationServerConfiguration.class, AUTHORIZATION_SERVER_EXCLUSIONS);
  }

  @Test
  void clientRepresentationCarriesEveryField() {
    assertRoundTrippable(ClientConfiguration.class, CLIENT_EXCLUSIONS);
  }

  /**
   * #1845: an extension is nested inside its parent representation, so the tests above only see
   * that the {@code extension} key is present, not what it carries. Each extension needs the same
   * ledger of its own; {@code fapi20_scopes} was dropped for exactly this reason.
   */
  @Test
  void authorizationServerExtensionRepresentationCarriesEveryField() {
    assertRoundTrippable(AuthorizationServerExtensionConfiguration.class, Set.of());
  }

  @Test
  void clientExtensionRepresentationCarriesEveryField() {
    assertRoundTrippable(ClientExtensionConfiguration.class, CLIENT_EXTENSION_EXCLUSIONS);
  }

  private <T> void assertRoundTrippable(Class<T> type, Set<String> exclusions) {
    Map<String, Object> populated = new HashMap<>();
    List<String> unsupported = new ArrayList<>();

    for (Field field : declaredFields(type)) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Object value = sampleValue(field);
      if (value == null) {
        unsupported.add(snakeCase(field.getName()));
        continue;
      }
      populated.put(snakeCase(field.getName()), value);
    }

    T configuration = jsonConverter.read(populated, type);
    Map<String, Object> representation = toMap(configuration);
    Set<String> exposed = representation.keySet();

    List<String> dropped =
        populated.keySet().stream()
            .filter(key -> !exposed.contains(key))
            .filter(key -> !exclusions.contains(key))
            .sorted()
            .toList();

    assertTrue(
        dropped.isEmpty(),
        type.getSimpleName()
            + ".toMap() drops fields that the update reads back, so a GET -> modify -> PUT round"
            + " trip loses them: "
            + dropped);

    // Presence alone is not enough: a masked or rewritten value round trips into a different
    // configuration just as silently as a missing key does.
    List<String> altered =
        populated.entrySet().stream()
            .filter(entry -> exposed.contains(entry.getKey()))
            .filter(entry -> !Objects.equals(entry.getValue(), representation.get(entry.getKey())))
            .map(
                entry ->
                    entry.getKey()
                        + ": "
                        + entry.getValue()
                        + " -> "
                        + representation.get(entry.getKey()))
            .sorted()
            .toList();

    assertTrue(
        altered.isEmpty(),
        type.getSimpleName()
            + ".toMap() returns values a PUT cannot write back unchanged: "
            + altered);

    // Nested configurations carry their own representation; they are covered by their own tests.
    // Listing them here keeps the ones this test cannot populate visible.
    assertEquals(
        exclusions,
        unsupported.stream().filter(key -> !exposed.contains(key)).collect(toSet()),
        "unexposed fields of a type this test cannot populate must be listed as exclusions");
  }

  private static java.util.stream.Collector<String, ?, Set<String>> toSet() {
    return java.util.stream.Collectors.toSet();
  }

  /** Walks the hierarchy so that a field pulled up into a base class stays covered. */
  private List<Field> declaredFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null && current != Object.class; ) {
      fields.addAll(Arrays.asList(current.getDeclaredFields()));
      current = current.getSuperclass();
    }
    return fields;
  }

  @SuppressWarnings("unchecked")
  private <T> Map<String, Object> toMap(T configuration) {
    try {
      return (Map<String, Object>)
          configuration.getClass().getMethod("toMap").invoke(configuration);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Returns a non-default value for the field, or null when the type is not a plain value. */
  private Object sampleValue(Field field) {
    Class<?> type = field.getType();

    if (type == String.class) {
      return "value-" + field.getName();
    }
    if (type == boolean.class || type == Boolean.class) {
      return true;
    }
    if (type == int.class || type == Integer.class) {
      return 7;
    }
    if (type == long.class || type == Long.class) {
      return 7L;
    }
    if (List.class.isAssignableFrom(type)) {
      return elementType(field) == String.class ? List.of("value") : null;
    }
    if (Map.class.isAssignableFrom(type)) {
      // A map of lists needs a list as its value, or the sample cannot be deserialized back.
      return valueType(field) instanceof ParameterizedType parameterized
              && List.class.isAssignableFrom((Class<?>) parameterized.getRawType())
          ? Map.of("key", List.of("value"))
          : Map.of("key", "value");
    }
    return null;
  }

  private Type valueType(Field field) {
    if (field.getGenericType() instanceof ParameterizedType parameterized) {
      Type[] arguments = parameterized.getActualTypeArguments();
      if (arguments.length == 2) {
        return arguments[1];
      }
    }
    return Object.class;
  }

  private Type elementType(Field field) {
    if (field.getGenericType() instanceof ParameterizedType parameterized) {
      return parameterized.getActualTypeArguments()[0];
    }
    return Object.class;
  }

  private static String snakeCase(String name) {
    return name.replaceAll("(?<!^)([A-Z])", "_$1").toLowerCase();
  }
}
