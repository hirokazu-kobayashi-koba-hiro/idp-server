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
import java.util.Set;
import org.idp.server.platform.mapper.functions.FunctionRegistry;
import org.idp.server.platform.mapper.functions.ValueFunction;

/**
 * A fixed sequence of value functions, applied in order, for code that uses the result to decide
 * something — most often whether two values are the same — and so needs the functions a mapping
 * rule uses, but stricter.
 *
 * <p>Built with the functions the caller allows ({@link #forComparison} for comparing values), and
 * checked when built rather than when first applied:
 *
 * <ul>
 *   <li>An unknown function, or one the caller does not allow, is an error. The mapper logs and
 *       skips an unknown name, which is tolerable when shaping data but would quietly change what
 *       is being compared. And not every function suits a comparison: {@code exists} answers true
 *       for anything non-empty, so two values passed through it always match; {@code random_string}
 *       or {@code now} never do.
 *   <li>Each function is tried once on a representative value, so a wrong argument — a {@code kana}
 *       target that does not exist, a {@code date} format asking for the hour, a {@code case} mode
 *       that is not one — is found when the configuration is read, not on the first real value, and
 *       not only for the inputs that happen to reach the check.
 *   <li>Arguments are used as written. {@code "$."} paths are not resolved, so nothing outside the
 *       value being transformed can influence how it is transformed.
 * </ul>
 */
public class ValueFunctionChain {

  /**
   * Functions that transform a string the same way every time and depend on nothing else, which is
   * what passing two values through the same chain and comparing them requires.
   */
  public static final Set<String> COMPARISON_FUNCTIONS =
      Set.of("normalize", "trim", "case", "replace", "regex_replace", "substring", "kana", "date");

  /**
   * What each function is tried on when the chain is built: non-empty, so every check is reached.
   */
  static final String PROBE = "\u3042\u30A2 Aa1 2000-01-01";

  private static final FunctionRegistry functionRegistry = new FunctionRegistry();

  List<FunctionSpec> specs;

  private ValueFunctionChain(List<FunctionSpec> specs) {
    this.specs = specs;
  }

  /**
   * A chain for comparing values: only {@link #COMPARISON_FUNCTIONS}.
   *
   * @throws IllegalArgumentException if a function is not allowed, or its arguments are wrong
   */
  public static ValueFunctionChain forComparison(List<FunctionSpec> specs) {
    return of(specs, COMPARISON_FUNCTIONS);
  }

  /**
   * @param allowedFunctions the function names this chain may contain
   * @throws IllegalArgumentException if a function is not named, not known, not allowed, or its
   *     arguments are wrong
   */
  public static ValueFunctionChain of(List<FunctionSpec> specs, Set<String> allowedFunctions) {
    if (specs == null) {
      return new ValueFunctionChain(List.of());
    }
    // Checked before copying: List.copyOf rejects a null element with a NullPointerException,
    // which a caller treating IllegalArgumentException as a configuration error would not catch.
    for (FunctionSpec spec : specs) {
      String name = spec == null ? null : spec.name();
      if (name == null || !functionRegistry.exists(name)) {
        throw new IllegalArgumentException("unknown function: " + name);
      }
      if (!allowedFunctions.contains(name)) {
        throw new IllegalArgumentException("function not allowed here: " + name);
      }
      probe(spec);
    }
    return new ValueFunctionChain(List.copyOf(specs));
  }

  private static void probe(FunctionSpec spec) {
    try {
      functionRegistry.get(spec.name()).apply(PROBE, argsOf(spec));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "invalid arguments for function " + spec.name() + ": " + e.getMessage(), e);
    }
  }

  /** Applies each function in turn; a function's output is the next one's input. */
  public Object apply(Object input) {
    Object value = input;
    for (FunctionSpec spec : specs) {
      ValueFunction function = functionRegistry.get(spec.name());
      value = function.apply(value, argsOf(spec));
    }
    return value;
  }

  private static Map<String, Object> argsOf(FunctionSpec spec) {
    return spec.args() == null ? Map.of() : spec.args();
  }

  public boolean isEmpty() {
    return specs.isEmpty();
  }

  public List<FunctionSpec> specs() {
    return specs;
  }
}
