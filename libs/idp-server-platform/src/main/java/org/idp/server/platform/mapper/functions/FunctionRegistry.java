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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FunctionRegistry {
  private final Map<String, ValueFunction> map;

  public FunctionRegistry() {
    Map<String, ValueFunction> temp = new HashMap<>();

    FormatFunction formatFunction = new FormatFunction();
    temp.put(formatFunction.name(), formatFunction);

    RandomStringFunction randomStringFunction = new RandomStringFunction();
    temp.put(randomStringFunction.name(), randomStringFunction);

    NowFunction nowFunction = new NowFunction();
    temp.put(nowFunction.name(), nowFunction);

    ExistsFunction existsFunction = new ExistsFunction();
    temp.put(existsFunction.name(), existsFunction);

    ConvertTypeFunction convertTypeFunction = new ConvertTypeFunction();
    temp.put(convertTypeFunction.name(), convertTypeFunction);

    Uuid4Function uuid4Function = new Uuid4Function();
    temp.put(uuid4Function.name(), uuid4Function);

    Uuid5Function uuid5Function = new Uuid5Function();
    temp.put(uuid5Function.name(), uuid5Function);

    UuidShortFunction uuidShortFunction = new UuidShortFunction();
    temp.put(uuidShortFunction.name(), uuidShortFunction);

    SubstringFunction substringFunction = new SubstringFunction();
    temp.put(substringFunction.name(), substringFunction);

    ReplaceFunction replaceFunction = new ReplaceFunction();
    temp.put(replaceFunction.name(), replaceFunction);

    RegexReplaceFunction regexReplaceFunction = new RegexReplaceFunction();
    temp.put(regexReplaceFunction.name(), regexReplaceFunction);

    CaseFunction caseFunction = new CaseFunction();
    temp.put(caseFunction.name(), caseFunction);

    TrimFunction trimFunction = new TrimFunction();
    temp.put(trimFunction.name(), trimFunction);

    IfFunction ifFunction = new IfFunction();
    temp.put(ifFunction.name(), ifFunction);

    SwitchFunction switchFunction = new SwitchFunction();
    temp.put(switchFunction.name(), switchFunction);

    // Collection operation functions
    MapFunction mapFunction = new MapFunction();
    temp.put(mapFunction.name(), mapFunction);

    FilterFunction filterFunction = new FilterFunction();
    temp.put(filterFunction.name(), filterFunction);

    JoinFunction joinFunction = new JoinFunction();
    temp.put(joinFunction.name(), joinFunction);

    SplitFunction splitFunction = new SplitFunction();
    temp.put(splitFunction.name(), splitFunction);

    // Initialize map field before calling setFunctionRegistry to prevent partial construction
    // escape
    this.map = Collections.unmodifiableMap(temp);

    // Set registry reference after map initialization to ensure thread-safety
    mapFunction.setFunctionRegistry(this);
  }

  public ValueFunction get(String name) {
    return map.get(name);
  }

  public boolean exists(String name) {
    return map.containsKey(name);
  }
}
