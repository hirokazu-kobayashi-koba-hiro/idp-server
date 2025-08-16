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

import java.util.Map;

public class FormatFunction implements ValueFunction {
  @Override
  public Object apply(Object input, Map<String, Object> args) {
    String template = (String) args.getOrDefault("template", "{{value}}");
    String value = input == null ? "" : input.toString();

    return template.replace("{{value}}", value);
  }

  @Override
  public String name() {
    return "format";
  }
}
