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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.functions.FunctionRegistry;
import org.idp.server.platform.mapper.functions.ValueFunction;

/**
 * Executes mapping rules to transform source data into a target structure.
 *
 * <p>This is the core engine for all data mapping in idp-server: body_mapping_rules,
 * header_mapping_rules, verified_claims_mapping_rules, etc.
 *
 * <h3>Processing Phases (per MappingRule)</h3>
 *
 * <ol>
 *   <li><b>Condition evaluation</b> — If a condition is defined, evaluate it against the source
 *       data. Skip this rule if false.
 *   <li><b>Input resolution (resolveBaseValue)</b> — Determine the input value for functions:
 *       <ul>
 *         <li>{@code static_value} — Use the literal value as-is
 *         <li>{@code from} — Evaluate the JSONPath against source data to extract the value
 *         <li>Neither — input is {@code null}
 *       </ul>
 *   <li><b>Function application (applyFunctions)</b> — For each function in the {@code functions}
 *       array:
 *       <ul>
 *         <li>Resolve dynamic args (see below)
 *         <li>Call {@code ValueFunction.apply(input, resolvedArgs)}
 *         <li>The output becomes the input for the next function in the chain
 *       </ul>
 *   <li><b>Result writing (writeResult)</b> — Write the final value to the output map:
 *       <ul>
 *         <li>{@code to: "*"} — Expand a Map value into the output (putAll)
 *         <li>{@code to: "a.b.c"} — Write to a nested path, reconstructed by {@link
 *             ObjectCompositor}
 *       </ul>
 * </ol>
 *
 * <h3>Value Resolution — Three Types</h3>
 *
 * <p>There are three distinct points where values are resolved from the source data:
 *
 * <table border="1">
 *   <tr><th>Type</th><th>Source</th><th>Resolved to</th><th>Example</th></tr>
 *   <tr>
 *     <td>{@code from}</td>
 *     <td>MappingRule.from</td>
 *     <td>Function input</td>
 *     <td>{@code "from": "$.user.accounts"} → the accounts array</td>
 *   </tr>
 *   <tr>
 *     <td>{@code static_value}</td>
 *     <td>MappingRule.staticValue</td>
 *     <td>Function input (literal)</td>
 *     <td>{@code "static_value": null} → null (for uuid4, now, etc.)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code args} (dynamic)</td>
 *     <td>FunctionSpec.args values starting with "$."</td>
 *     <td>Function argument</td>
 *     <td>{@code "source": "$.request_body.new_accounts"} → resolved array</td>
 *   </tr>
 * </table>
 *
 * <h3>Dynamic Args Resolution</h3>
 *
 * <p>Function args values that are strings starting with {@code "$."} are treated as JSONPath
 * expressions and resolved against the source data before the function is called. Static values
 * (numbers, booleans, strings not starting with "$." ) are passed through unchanged. This is
 * handled by {@link #resolveArgs(Map, JsonPathWrapper)}.
 *
 * <pre>{@code
 * // Config:
 * {"name": "merge", "args": {"source": "$.request_body.new_accounts", "key": "account_no"}}
 *
 * // At runtime, resolveArgs transforms:
 * {"source": "$.request_body.new_accounts", "key": "account_no"}
 *       ↓
 * {"source": [{account_no: "333", type: "investment"}], "key": "account_no"}
 *             ↑ resolved from source data                 ↑ static, unchanged
 * }</pre>
 *
 * <h3>Function Chaining</h3>
 *
 * <p>When multiple functions are specified, they execute left-to-right. Each function's output
 * becomes the next function's input. Args are resolved independently for each function.
 *
 * <pre>{@code
 * // merge then pluck:
 * "functions": [
 *   {"name": "merge", "args": {"source": "$.request_body.new_accounts", "key": "account_no"}},
 *   {"name": "pluck", "args": {"field": "account_no"}}
 * ]
 *
 * // Execution:
 * input: [{account_no: "111"}, {account_no: "222"}]
 *   → merge(input, {source: [resolved], key: "account_no"})
 *   → [{account_no: "111"}, {account_no: "222"}, {account_no: "333"}]
 *   → pluck(merged, {field: "account_no"})
 *   → ["111", "222", "333"]
 * }</pre>
 */
public class MappingRuleObjectMapper {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(MappingRuleObjectMapper.class);
  private static final FunctionRegistry functionRegistry = new FunctionRegistry();
  private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  /**
   * Execute mapping rules against the source data and produce an output map.
   *
   * @param mappingRules the list of mapping rules to apply
   * @param jsonPath the source data wrapped for JSONPath evaluation
   * @return the mapped output as a nested Map structure
   */
  public static Map<String, Object> execute(
      List<MappingRule> mappingRules, JsonPathWrapper jsonPath) {

    logInputs(mappingRules, jsonPath);

    Map<String, Object> flatMap = new LinkedHashMap<>();

    for (MappingRule rule : mappingRules) {
      // Check condition before executing the rule
      if (!rule.shouldExecute(jsonPath)) {
        log.debug("Rule skipped due to condition: to={}", rule.to());
        continue;
      }

      Object baseValue = resolveBaseValue(rule, jsonPath);
      Object finalValue = applyFunctions(rule, baseValue, jsonPath);
      writeResult(rule, finalValue, flatMap);
    }

    Map<String, Object> result = new ObjectCompositor(flatMap).composite();

    logOutput(result);

    return result;
  }

  /**
   * Resolve the base input value for a mapping rule.
   *
   * <p>Resolution priority:
   *
   * <ol>
   *   <li>{@code static_value} — Return the literal value (for uuid4, now, etc.)
   *   <li>{@code from} — Evaluate JSONPath against source data (e.g., {@code
   *       "$.user.verified_claims.claims.accounts"})
   *   <li>Neither — Return {@code null}
   * </ol>
   *
   * <p>The resolved value becomes the {@code input} parameter of {@link ValueFunction#apply(Object,
   * Map)}.
   */
  static Object resolveBaseValue(MappingRule rule, JsonPathWrapper jsonPath) {
    if (rule.hasStaticValue()) {
      log.debug("Apply static value: to={}", rule.to());
      return rule.staticValue();
    }
    if (rule.hasFrom()) {
      log.debug("Read from JSONPath: from={}, to={}", rule.from(), rule.to());
      return jsonPath.readRaw(rule.from());
    }
    // no static/from → start from null
    return null;
  }

  /**
   * Apply all configured functions sequentially (function chaining).
   *
   * <p>For each function in the {@code functions} array:
   *
   * <ol>
   *   <li>Look up the function by name from {@link FunctionRegistry}
   *   <li>Resolve dynamic args via {@link #resolveArgs(Map, JsonPathWrapper)}
   *   <li>Call {@code fn.apply(currentValue, resolvedArgs)}
   *   <li>The return value becomes {@code currentValue} for the next function
   * </ol>
   *
   * @param rule the mapping rule containing the functions list
   * @param value the initial input value (from {@link #resolveBaseValue})
   * @param jsonPath the source data context, used for dynamic args resolution
   * @return the final value after all functions have been applied
   */
  static Object applyFunctions(MappingRule rule, Object value, JsonPathWrapper jsonPath) {
    if (!rule.hasFunctions()) {
      return value;
    }
    Object v = value;
    for (FunctionSpec spec : rule.functions()) {
      ValueFunction fn = functionRegistry.get(spec.name());
      if (fn == null) {
        log.error("Function not found: name={}, to={}", spec.name(), rule.to());
        continue;
      }
      Map<String, Object> resolvedArgs = resolveArgs(spec.args(), jsonPath);
      v = fn.apply(v, resolvedArgs);
    }
    return v;
  }

  /**
   * Resolve JSONPath references in function args before passing them to a ValueFunction.
   *
   * <p>This method scans each value in the args map. If a value is a String starting with {@code
   * "$."}, it is treated as a JSONPath expression and evaluated against the source data. All other
   * values (numbers, booleans, strings not starting with "$.", objects, arrays) are passed through
   * unchanged.
   *
   * <p><b>Resolution rules:</b>
   *
   * <ul>
   *   <li>{@code "$.request_body.name"} → resolved to the value at that JSONPath
   *   <li>{@code "account_no"} → unchanged (no "$." prefix)
   *   <li>{@code 42} → unchanged (not a String)
   *   <li>{@code true} → unchanged (not a String)
   *   <li>{@code "$.nonexistent.path"} → resolved to {@code null}
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Input args:
   * {"source": "$.request_body.new_accounts", "key": "account_no", "distinct": true}
   *
   * // Source data:
   * {"request_body": {"new_accounts": [{"account_no": "333"}]}}
   *
   * // Output (resolved args):
   * {"source": [{"account_no": "333"}], "key": "account_no", "distinct": true}
   * }</pre>
   *
   * @param args the raw function args from configuration (may contain JSONPath strings)
   * @param jsonPath the source data context for JSONPath evaluation
   * @return a new map with JSONPath values resolved; null/empty args are returned as-is
   */
  static Map<String, Object> resolveArgs(Map<String, Object> args, JsonPathWrapper jsonPath) {
    if (args == null || args.isEmpty()) {
      return args;
    }
    Map<String, Object> resolved = new HashMap<>(args);
    for (Map.Entry<String, Object> entry : resolved.entrySet()) {
      if (entry.getValue() instanceof String str && str.startsWith("$.")) {
        entry.setValue(jsonPath.readRaw(str));
      }
    }
    return resolved;
  }

  /**
   * Write the final value to the output map.
   *
   * <p>If {@code to} is {@code "*"}, the value must be a Map and its entries are expanded into the
   * output (putAll semantics). Otherwise, the value is written at the specified key path (e.g.,
   * {@code "claims.given_name"}), later reconstructed into nested structure by {@link
   * ObjectCompositor}.
   */
  static void writeResult(MappingRule rule, Object value, Map<String, Object> flatMap) {
    if ("*".equals(rule.to())) {
      if (value instanceof Map<?, ?> mapValue) {
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
          if (entry.getKey() instanceof String key) {
            flatMap.put(key, entry.getValue());
          }
        }
      } else if (value == null) {
        log.debug(
            "'*' skipped: value is null (from=" + (rule.hasFrom() ? rule.from() : "n/a") + ")");
      } else {
        log.error("'*' requires Map but got: type={}", value.getClass().getSimpleName());
      }
    } else {
      flatMap.put(rule.to(), value);
    }
  }

  /** Log inputs for debugging: mapping rules and source JSON. */
  private static void logInputs(List<MappingRule> mappingRules, JsonPathWrapper jsonPath) {
    try {
      List<Map<String, Object>> rulesAsMap =
          mappingRules.stream().map(MappingRule::toMap).collect(Collectors.toList());

      String rulesJson = jsonConverter.write(rulesAsMap);
      String inputJson = jsonPath.toJson();

      log.debug("MappingRuleObjectMapper INPUT - Mapping Rules: {}", rulesJson);
      log.debug("MappingRuleObjectMapper INPUT - Source JSON: {}", inputJson);
    } catch (Exception e) {
      log.warn("Failed to log inputs: {}", e.getMessage());
    }
  }

  /** Log output for debugging: mapped result object. */
  private static void logOutput(Map<String, Object> result) {
    try {
      String outputJson = jsonConverter.write(result);
      log.debug("MappingRuleObjectMapper OUTPUT - Result: {}", outputJson);
    } catch (Exception e) {
      log.warn("Failed to log output: {}", e.getMessage());
    }
  }
}
