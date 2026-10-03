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

import java.util.List;
import java.util.Map;
import org.idp.server.platform.mapper.functions.FunctionRegistry;
import org.idp.server.platform.mapper.functions.ValueFunction;

/**
 * A fixed sequence of value functions, applied in order, for code that needs the same functions a
 * mapping rule uses but stricter.
 *
 * <p>Two things differ from {@link MappingRuleObjectMapper}'s function application, and both are
 * about using the result to decide something, such as whether two values are the same:
 *
 * <ul>
 *   <li>An unknown function name is an error when the chain is built. The mapper logs and skips it,
 *       which is tolerable when shaping data but would quietly change what is being compared.
 *   <li>Arguments are used as written. {@code "$."} paths are not resolved, so nothing outside the
 *       value being transformed can influence how it is transformed.
 * </ul>
 */
public class ValueFunctionChain {

  private static final FunctionRegistry functionRegistry = new FunctionRegistry();

  List<FunctionSpec> specs;

  private ValueFunctionChain(List<FunctionSpec> specs) {
    this.specs = specs;
  }

  /**
   * @throws IllegalArgumentException if a function is not named, or not known
   */
  public static ValueFunctionChain of(List<FunctionSpec> specs) {
    List<FunctionSpec> chain = specs == null ? List.of() : List.copyOf(specs);
    for (FunctionSpec spec : chain) {
      if (spec == null || spec.name() == null || !functionRegistry.exists(spec.name())) {
        throw new IllegalArgumentException(
            "unknown function: " + (spec == null ? null : spec.name()));
      }
    }
    return new ValueFunctionChain(chain);
  }

  /** Applies each function in turn; a function's output is the next one's input. */
  public Object apply(Object input) {
    Object value = input;
    for (FunctionSpec spec : specs) {
      ValueFunction function = functionRegistry.get(spec.name());
      Map<String, Object> args = spec.args() == null ? Map.of() : spec.args();
      value = function.apply(value, args);
    }
    return value;
  }

  public boolean isEmpty() {
    return specs.isEmpty();
  }

  public List<FunctionSpec> specs() {
    return specs;
  }
}
