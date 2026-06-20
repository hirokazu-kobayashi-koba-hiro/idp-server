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

package org.idp.server.platform.text;

/**
 * Escapes the HTML metacharacters {@code & < > " '} so RP-supplied text can be safely embedded in a
 * UI without enabling HTML/script injection (XSS).
 */
public class HtmlEscaper {

  private HtmlEscaper() {}

  public static String escape(String input) {
    if (input == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '&' -> builder.append("&amp;");
        case '<' -> builder.append("&lt;");
        case '>' -> builder.append("&gt;");
        case '"' -> builder.append("&quot;");
        case '\'' -> builder.append("&#39;");
        default -> builder.append(c);
      }
    }
    return builder.toString();
  }
}
