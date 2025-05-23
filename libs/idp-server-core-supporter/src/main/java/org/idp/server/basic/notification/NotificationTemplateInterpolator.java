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

package org.idp.server.basic.notification;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationTemplateInterpolator {

  String template;
  Map<String, Object> context;

  public NotificationTemplateInterpolator(String template, Map<String, Object> context) {
    this.template = template;
    this.context = context;
  }

  public String interpolate() {
    String result = this.template;

    for (String placeholder : extractPlaceholders(template)) {
      Object value = resolveValue(context, placeholder);
      result = result.replace("${" + placeholder + "}", value != null ? value.toString() : "");
    }

    return result;
  }

  private Set<String> extractPlaceholders(String template) {
    Set<String> placeholders = new HashSet<>();
    Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(template);
    while (matcher.find()) {
      placeholders.add(matcher.group(1));
    }
    return placeholders;
  }

  private static Object resolveValue(Map<String, Object> map, String path) {
    String[] parts = path.split("\\.");
    Object current = map;
    for (String part : parts) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<?, ?>) current).get(part);
      if (current == null) {
        return null;
      }
    }
    return current;
  }
}
