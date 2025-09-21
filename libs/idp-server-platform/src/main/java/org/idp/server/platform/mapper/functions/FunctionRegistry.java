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

import java.util.HashMap;
import java.util.Map;

public class FunctionRegistry {
  private final Map<String, ValueFunction> map = new HashMap<>();

  public FunctionRegistry() {
    register(new FormatFunction());
    register(new RandomStringFunction());
    register(new NowFunction());
    register(new ExistsFunction());
    register(new ConvertTypeFunction());
    register(new Uuid4Function());
    register(new Uuid5Function());
    register(new UuidShortFunction());
    register(new SubstringFunction());
    register(new ReplaceFunction());
    register(new RegexReplaceFunction());
    register(new CaseFunction());
    register(new TrimFunction());
    register(new IfFunction());
    register(new SwitchFunction());

    // Collection operation functions
    MapFunction mapFunction = new MapFunction();
    register(mapFunction);
    mapFunction.setFunctionRegistry(this);

    register(new FilterFunction());
    register(new JoinFunction());
    register(new SplitFunction());
  }

  public void register(ValueFunction fn) {
    map.put(fn.name(), fn);
  }

  public ValueFunction get(String name) {
    return map.get(name);
  }

  public boolean exists(String name) {
    return map.containsKey(name);
  }
}
