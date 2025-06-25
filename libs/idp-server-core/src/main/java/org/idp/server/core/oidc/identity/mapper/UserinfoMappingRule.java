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

package org.idp.server.core.oidc.identity.mapper;

import org.idp.server.platform.json.JsonReadable;

public class UserinfoMappingRule implements JsonReadable {
  String source;
  String from;
  String to;
  String type;
  Integer itemIndex;
  String field;

  public UserinfoMappingRule() {}

  public UserinfoMappingRule(String source, String from, String to, String type) {
    this.source = source;
    this.from = from;
    this.to = to;
    this.type = type;
  }

  public UserinfoMappingRule(
      String source, String from, String to, String type, Integer itemIndex, String field) {
    this.source = source;
    this.from = from;
    this.to = to;
    this.type = type;
    this.itemIndex = itemIndex;
    this.field = field;
  }

  public String getSource() {
    return source;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getType() {
    return type;
  }

  public int getItemIndexOrDefault(int defaultValue) {
    return itemIndex != null ? itemIndex : defaultValue;
  }

  public boolean hasItemIndex() {
    return itemIndex != null;
  }

  public String getFieldOrDefault(String defaultField) {
    return field != null ? field : defaultField;
  }
}
