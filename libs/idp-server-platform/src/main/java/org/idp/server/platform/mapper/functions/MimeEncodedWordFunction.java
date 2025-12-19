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
import java.util.Base64;
import java.util.Map;

/**
 * MIME Encoded-Word function (RFC 2047).
 *
 * <p>Encodes non-ASCII text for use in email headers (From, To, Subject, etc.).
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * {
 *   "function": "mimeEncodedWord",
 *   "args": {
 *     "charset": "UTF-8",        // Optional, default: UTF-8
 *     "encoding": "B"            // Optional, B=Base64, Q=Quoted-Printable, default: B
 *   }
 * }
 * }</pre>
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * Input: "日本語サービス"
 * Output: "=?UTF-8?B?5pel5pys6Kqe44K144O844OT44K5?="
 *
 * Input: "Example Service"
 * Output: "Example Service" (ASCII only, no encoding)
 * }</pre>
 *
 * <h2>RFC 2047 Format</h2>
 *
 * <pre>
 * =?charset?encoding?encoded-text?=
 *
 * - charset: UTF-8, ISO-2022-JP, etc.
 * - encoding: B (Base64) or Q (Quoted-Printable)
 * - encoded-text: encoded text
 * </pre>
 *
 * <h2>Limitations</h2>
 *
 * <ul>
 *   <li><b>75-character limit</b>: RFC 2047 specifies that encoded-words must not exceed 75
 *       characters. This implementation does NOT enforce this limit. For typical sender names
 *       (20-30 characters), this is not an issue. If you need to encode longer text, consider
 *       using {@code jakarta.mail.internet.MimeUtility.encodeWord()}.
 *   <li><b>Simplified Q-encoding</b>: The Quoted-Printable implementation is simplified and may
 *       not handle all edge cases defined in RFC 2047.
 * </ul>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc2047">RFC 2047</a>
 */
public class MimeEncodedWordFunction implements ValueFunction {

  @Override
  public String name() {
    return "mimeEncodedWord";
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    String text = input.toString();

    // If text contains only ASCII characters, no encoding needed
    if (isAsciiOnly(text)) {
      return text;
    }

    // Get charset from args, default to UTF-8
    String charsetName = getStringArg(args, "charset", "UTF-8");
    Charset charset = Charset.forName(charsetName);

    // Get encoding from args, default to B (Base64)
    String encoding = getStringArg(args, "encoding", "B");

    if ("B".equalsIgnoreCase(encoding)) {
      return encodeBase64(text, charset, charsetName);
    } else if ("Q".equalsIgnoreCase(encoding)) {
      return encodeQuotedPrintable(text, charset, charsetName);
    } else {
      throw new IllegalArgumentException("Unsupported encoding: " + encoding + " (use B or Q)");
    }
  }

  private boolean isAsciiOnly(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) > 127) {
        return false;
      }
    }
    return true;
  }

  private String encodeBase64(String text, Charset charset, String charsetName) {
    byte[] bytes = text.getBytes(charset);
    String encoded = Base64.getEncoder().encodeToString(bytes);
    return String.format("=?%s?B?%s?=", charsetName, encoded);
  }

  private String encodeQuotedPrintable(String text, Charset charset, String charsetName) {
    // Simplified Quoted-Printable encoding
    byte[] bytes = text.getBytes(charset);
    StringBuilder encoded = new StringBuilder();

    for (byte b : bytes) {
      int ch = b & 0xFF;
      // Encode non-printable and special characters
      if (ch < 32 || ch > 126 || ch == '=' || ch == '?' || ch == '_') {
        encoded.append(String.format("=%02X", ch));
      } else if (ch == 32) {
        encoded.append('_'); // Space as underscore in Q-encoding
      } else {
        encoded.append((char) ch);
      }
    }

    return String.format("=?%s?Q?%s?=", charsetName, encoded);
  }

  private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
    if (args == null || !args.containsKey(key)) {
      return defaultValue;
    }
    Object value = args.get(key);
    return value != null ? value.toString() : defaultValue;
  }
}
