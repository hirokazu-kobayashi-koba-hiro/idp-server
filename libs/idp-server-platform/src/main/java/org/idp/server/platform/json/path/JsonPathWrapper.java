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

public class JsonPathWrapper {

  Object document;

  public JsonPathWrapper(Object jsonSource) {
    Configuration conf = Configuration.defaultConfiguration();
    this.document = conf.jsonProvider().parse(jsonSource.toString());
  }

  public String readAsString(String path) {
    return JsonPath.read(document, path);
  }

  public Integer readAsInt(String path) {
    return JsonPath.read(document, path);
  }

  public Boolean readAsBoolean(String path) {
    return JsonPath.read(document, path);
  }

  public List<String> readAsStringList(String path) {
    return JsonPath.read(document, path);
  }

  public List<Map<String, Object>> readAsMapList(String path) {
    return JsonPath.read(document, path);
  }

  public Map<String, Object> readAsMap(String path) {
    return JsonPath.read(document, path);
  }

  public Map<String, String> readAsStringMap(String path) {
    return JsonPath.read(document, path);
  }

  public Object readRaw(String path) {
    return JsonPath.read(document, path);
  }
}
