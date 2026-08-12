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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Base64;
import java.util.Map;

/**
 * Base64 encoding function (RFC 4648).
 *
 * <p>Lets a mapping rule carry the raw value and derive the encoded form at request time, instead
 * of storing a pre-encoded string alongside the original. The motivating case is {@code
 * client_secret_basic} against an external API: the credentials stay as-is in configuration and the
 * {@code Authorization} header is assembled here.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * {
 *   "name": "base64",
 *   "args": {
 *     "url_safe": false,   // Optional, RFC 4648 §5 alphabet when true, default: false
 *     "padding": true,     // Optional, emit '=' padding, default: true
 *     "charset": "UTF-8"   // Optional, default: UTF-8
 *     // Unusable values are rejected: an unknown charset, or a boolean that is
 *     // neither true/false, raises IllegalArgumentException rather than silently
 *     // falling back.
 *   }
 * }
 * }</pre>
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * Input: "id:secret"                             Output: "aWQ6c2VjcmV0"
 * Input: "" (empty string)                       Output: ""
 * Input: "ÿþ", url_safe=true, padding=false      Output: "w7_Dvg"
 * }</pre>
 *
 * <p>Combine with {@code format} to build a complete header value:
 *
 * <pre>{@code
 * {
 *   "static_value": "<client_id>:<client_secret>",
 *   "to": "Authorization",
 *   "functions": [
 *     { "name": "base64" },
 *     { "name": "format", "args": { "template": "Basic {{value}}" } }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code url_safe: true} with {@code padding: false} produces Base64URL, the form used when a
 * value has to travel in a URL or a JWT segment.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4648">RFC 4648</a>
 */
public class Base64Function implements ValueFunction {

  @Override
  public String name() {
    return "base64";
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    boolean urlSafe = getBooleanArg(args, "url_safe", false);
    boolean padding = getBooleanArg(args, "padding", true);
    Charset charset = resolveCharset(getStringArg(args, "charset", "UTF-8"));

    Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
    if (!padding) {
      encoder = encoder.withoutPadding();
    }

    return encoder.encodeToString(input.toString().getBytes(charset));
  }

  private Charset resolveCharset(String charsetName) {
    try {
      return Charset.forName(charsetName);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw new IllegalArgumentException("Unsupported charset: " + charsetName, e);
    }
  }

  private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
    if (args == null || !args.containsKey(key)) {
      return defaultValue;
    }
    Object value = args.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  /**
   * Reads a boolean argument, accepting only a JSON boolean or the strings {@code "true"} / {@code
   * "false"}.
   *
   * <p>{@code Boolean.parseBoolean} maps everything that is not {@code "true"} to {@code false}, so
   * {@code "padding": "yes"} would silently flip the flag away from its default. An unusable
   * charset already fails fast, and a misconfigured flag is just as much a configuration error, so
   * both are reported rather than guessed.
   */
  private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
    if (args == null || !args.containsKey(key)) {
      return defaultValue;
    }
    Object value = args.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    String text = value.toString();
    if ("true".equalsIgnoreCase(text)) {
      return true;
    }
    if ("false".equalsIgnoreCase(text)) {
      return false;
    }
    throw new IllegalArgumentException(
        "Invalid boolean value for " + key + ": " + text + " (use true or false)");
  }
}
