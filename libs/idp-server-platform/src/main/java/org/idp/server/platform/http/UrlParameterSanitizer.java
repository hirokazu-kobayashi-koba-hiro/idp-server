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

package org.idp.server.platform.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.idp.server.platform.exception.MaliciousInputException;

public class UrlParameterSanitizer {

  private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("\\.\\.[\\\\/]");
  private static final Pattern URL_SCHEME_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:", Pattern.CASE_INSENSITIVE);
  private static final Pattern CRLF_PATTERN = Pattern.compile("[\\r\\n]");
  private static final Pattern UNSAFE_PATH_CHARS_PATTERN = Pattern.compile("[<>\"'`|;$]");

  private UrlParameterSanitizer() {}

  public static String sanitizePath(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    if (PATH_TRAVERSAL_PATTERN.matcher(value).find()) {
      throw MaliciousInputException.pathTraversal(value);
    }

    if (URL_SCHEME_PATTERN.matcher(value).find()) {
      throw MaliciousInputException.urlSchemeInjection(value);
    }

    if (CRLF_PATTERN.matcher(value).find()) {
      throw MaliciousInputException.crlfInjection(value);
    }

    if (UNSAFE_PATH_CHARS_PATTERN.matcher(value).find()) {
      throw MaliciousInputException.unsafeCharacters(value);
    }

    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public static String sanitizeQuery(String key, String value) {
    String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
    String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
    return encodedKey + "=" + encodedValue;
  }

  public static String encodeQueryKey(String key) {
    return URLEncoder.encode(key, StandardCharsets.UTF_8);
  }

  public static String encodeQueryValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
