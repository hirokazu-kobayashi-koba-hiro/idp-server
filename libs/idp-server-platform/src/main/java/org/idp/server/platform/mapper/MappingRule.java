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

package org.idp.server.platform.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class MappingRule implements JsonReadable {
  String from;
  Object staticValue;
  String to;
  String convertType;
  List<FunctionSpec> functions;

  public MappingRule() {}

  public MappingRule(String from, String to) {
    this.from = from;
    this.to = to;
  }

  public MappingRule(Object staticValue, String to) {
    this.staticValue = staticValue;
    this.to = to;
  }

  public MappingRule(String from, String to, String convertType) {
    this.from = from;
    this.to = to;
    this.convertType = convertType;
  }

  public String from() {
    return from;
  }

  public Object staticValue() {
    return staticValue;
  }

  public boolean hasStaticValue() {
    return staticValue != null;
  }

  public boolean hasFrom() {
    return from != null && !from.isEmpty();
  }

  public String to() {
    return to;
  }

  public String convertType() {
    return convertType;
  }

  public boolean hasConvertType() {
    return convertType != null && !convertType.isEmpty();
  }

  public boolean hasFunctions() {
    return functions != null && !functions.isEmpty();
  }

  public List<FunctionSpec> functions() {
    return functions;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("from", from);
    map.put("static_value", staticValue);
    map.put("to", to);
    map.put("convert_type", convertType);
    map.put("functions", functions());
    return map;
  }
}
