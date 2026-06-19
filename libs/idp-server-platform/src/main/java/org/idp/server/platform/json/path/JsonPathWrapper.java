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

package org.idp.server.platform.json.path;

import com.jayway.jsonpath.*;
import java.util.*;
import org.idp.server.platform.log.LoggerWrapper;

public class JsonPathWrapper {

  Object document;
  String originalJson;
  LoggerWrapper log = LoggerWrapper.getLogger(JsonPathWrapper.class);

  public JsonPathWrapper(String json) {
    Configuration conf = Configuration.defaultConfiguration();
    this.document = conf.jsonProvider().parse(json);
    this.originalJson = json;
  }

  public String toJson() {
    return originalJson;
  }

  public String readAsString(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public Integer readAsInt(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public Boolean readAsBoolean(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public List<String> readAsStringList(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public List<Map<String, Object>> readAsMapList(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public Map<String, Object> readAsMap(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public Map<String, String> readAsStringMap(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  public Object readRaw(String path) {
    try {
      return JsonPath.read(document, path);
    } catch (PathNotFoundException e) {
      return logMissingPath(e);
    }
  }

  /**
   * Logs a path-not-found and returns {@code null} for the read methods.
   *
   * <p>Path-not-found is a normal branch for callers: condition evaluation ({@code missing} /
   * {@code exists} / {@code ne} / {@code nin}) and mapping {@code from} resolution treat a missing
   * path as a valid {@code null}. It is logged at {@code debug} so it can be enabled on demand
   * without polluting {@code INFO} in production. (#1646)
   */
  private <T> T logMissingPath(PathNotFoundException e) {
    log.debug(e.getMessage());
    return null;
  }
}
