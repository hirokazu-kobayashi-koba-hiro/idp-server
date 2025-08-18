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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.idp.server.platform.date.LocalDateTimeParser;

/**
 * {@code ConvertTypeFunction} performs type conversion for a given input value.
 *
 * <p>This function supports the following conversion types:
 *
 * <ul>
 *   <li><b>string</b>: Converts the input to a {@link String} (with optional trimming).
 *   <li><b>integer</b>: Converts the input to an {@code int} (from {@link Number} or parseable
 *       string).
 *   <li><b>long</b>: Converts the input to a {@code long}.
 *   <li><b>double</b>: Converts the input to a {@code double}.
 *   <li><b>boolean</b>: Converts the input to a {@code boolean}. Recognized values: "1", "true",
 *       "yes", "y", "on" → {@code true}; "0", "false", "no", "n", "off" → {@code false}.
 *   <li><b>datetime</b>: Converts the input to a {@link LocalDateTime}, using {@link
 *       LocalDateTimeParser}.
 * </ul>
 *
 * <p>Error handling can be controlled with the following arguments:
 *
 * <ul>
 *   <li><b>onError</b>:
 *       <ul>
 *         <li>"null" (default): Return {@code null} on failure.
 *         <li>"default": Return the provided <b>default</b> value.
 *         <li>"throw": Throw an {@link IllegalArgumentException} on failure.
 *       </ul>
 *   <li><b>default</b>: A fallback value used when {@code onError = "default"}.
 *   <li><b>trim</b>: Whether to trim string input before conversion (default: {@code true}).
 *   <li><b>locale</b>: Locale to be used for case-insensitive operations (default: {@link
 *       Locale#ROOT}).
 * </ul>
 */
public class ConvertTypeFunction implements ValueFunction {

  /**
   * Applies type conversion based on the provided arguments.
   *
   * @param input the input value to be converted (may be {@code null})
   * @param args a map of arguments controlling the conversion behavior:
   *     <ul>
   *       <li>type: target type ("string", "integer", "long", "double", "boolean", "datetime")
   *       <li>onError: error handling strategy ("null", "default", "throw")
   *       <li>default: fallback value when {@code onError = "default"}
   *       <li>trim: whether to trim strings (true/false)
   *       <li>locale: locale tag for string operations
   *     </ul>
   *
   * @return the converted value, or a fallback/null depending on error handling
   */
  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) return null;

    final String type = str(args, "type", null);
    final String onError = str(args, "onError", "null"); // error handling strategy
    final Object defaultValue = args != null ? args.get("default") : null;
    final boolean trim = bool(args, "trim", true);
    final Locale locale = locale(args, "locale", Locale.ROOT);

    if (type == null || type.isEmpty()) return input;

    try {
      return switch (type) {
        case "string" -> {
          String s = String.valueOf(input);
          yield trim ? s.trim() : s;
        }
        case "integer" -> {
          if (input instanceof Number n) yield n.intValue();
          String s = String.valueOf(input);
          yield Integer.parseInt(trim ? s.trim() : s);
        }
        case "long" -> {
          if (input instanceof Number n) yield n.longValue();
          String s = String.valueOf(input);
          yield Long.parseLong(trim ? s.trim() : s);
        }
        case "double" -> {
          if (input instanceof Number n) yield n.doubleValue();
          String s = String.valueOf(input);
          yield Double.parseDouble(trim ? s.trim() : s);
        }
        case "boolean" -> {
          if (input instanceof Boolean) yield input;
          String s =
              (trim ? String.valueOf(input).trim() : String.valueOf(input)).toLowerCase(locale);
          if ("1".equals(s)
              || "true".equals(s)
              || "yes".equals(s)
              || "y".equals(s)
              || "on".equals(s)) yield true;
          if ("0".equals(s)
              || "false".equals(s)
              || "no".equals(s)
              || "n".equals(s)
              || "off".equals(s)) yield false;
          yield Boolean.parseBoolean(s);
        }
        case "datetime" -> {
          if (input instanceof LocalDateTime) yield input;
          yield LocalDateTimeParser.parse(String.valueOf(input));
        }
        default -> input; // unknown type: return input as-is (fail-open)
      };
    } catch (Exception e) {
      return switch (onError) {
        case "throw" -> {
          throw (e instanceof RuntimeException re) ? re : new IllegalArgumentException(e);
        }
        case "default" -> defaultValue;
        case "null" -> null;
        default -> null;
      };
    }
  }

  @Override
  public String name() {
    return "convert_type";
  }

  /** Helper method: retrieve a string argument from args with a default fallback. */
  private static String str(Map<String, Object> args, String key, String def) {
    if (args == null) return def;
    Object v = args.get(key);
    return v == null ? def : v.toString();
  }

  /** Helper method: retrieve a boolean argument from args with a default fallback. */
  private static boolean bool(Map<String, Object> args, String key, boolean def) {
    if (args == null) return def;
    Object v = args.get(key);
    if (v == null) return def;
    if (v instanceof Boolean b) return b;
    return Boolean.parseBoolean(v.toString());
  }

  /** Helper method: retrieve a locale argument from args with a default fallback. */
  private static Locale locale(Map<String, Object> args, String key, Locale def) {
    if (args == null) return def;
    Object v = args.get(key);
    if (v == null) return def;
    String s = v.toString();
    try {
      return Locale.forLanguageTag(s);
    } catch (Exception ignored) {
      return def;
    }
  }
}
