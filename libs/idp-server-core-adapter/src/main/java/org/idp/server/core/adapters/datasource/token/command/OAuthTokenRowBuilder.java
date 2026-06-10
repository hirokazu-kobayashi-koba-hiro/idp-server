/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.token.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper for building the JDBC {@code params} list and the cache-shaped {@code row} map in lockstep
 * during INSERT. The {@code row} produced here is consumed by the cache layer and must have the
 * same key set as {@code query.selectOneByAccessToken}'s {@code Map<String, String>} return value.
 *
 * <p>The {@code stringify} rules are aligned with {@link
 * org.idp.server.platform.date.LocalDateTimeParser}, which accepts both ISO ({@code
 * 2025-09-18T12:34:56}) and space-separated ({@code 2025-09-18 12:34:56}) formats. {@link
 * LocalDateTime#toString()} produces the ISO form, so the parser-side accepts it.
 */
final class OAuthTokenRowBuilder {

  private OAuthTokenRowBuilder() {}

  static void add(List<Object> params, Map<String, String> row, String column, Object value) {
    params.add(value);
    row.put(column, stringify(value));
  }

  static String stringify(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDateTime ldt) {
      return ldt.toString();
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    return value.toString();
  }
}
