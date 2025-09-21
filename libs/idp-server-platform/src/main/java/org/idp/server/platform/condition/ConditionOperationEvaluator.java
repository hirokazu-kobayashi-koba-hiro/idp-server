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

package org.idp.server.platform.condition;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Evaluates conditions for mapping rules with support for various comparison operations.
 *
 * <p>This class provides robust condition evaluation with regex caching and safety features.
 *
 * <p><b>Configurable System Properties:</b>
 *
 * <ul>
 *   <li><b>idp.condition.regex.maxLength</b>: Maximum regex pattern length (default: 1000)
 *   <li><b>idp.condition.regex.cacheSize</b>: Maximum regex cache size with LRU eviction (default:
 *       100)
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>Regex pattern length limiting to prevent ReDoS attacks
 *   <li>LRU cache with automatic eviction to prevent memory exhaustion
 *   <li>Thread-safe pattern compilation and caching
 * </ul>
 */
public class ConditionOperationEvaluator {

  // Configurable limits for operational flexibility
  private static final int MAX_REGEX_LENGTH =
      Integer.parseInt(System.getProperty("idp.condition.regex.maxLength", "1000"));
  private static final int MAX_CACHE_SIZE =
      Integer.parseInt(System.getProperty("idp.condition.regex.cacheSize", "100"));

  private static final Map<String, Pattern> PATTERN_CACHE = createLRUCache(MAX_CACHE_SIZE);

  /**
   * Creates a thread-safe LRU cache for compiled regex patterns. Uses LinkedHashMap with
   * access-order and removeEldestEntry override.
   */
  private static Map<String, Pattern> createLRUCache(int maxSize) {
    return Collections.synchronizedMap(
        new LinkedHashMap<String, Pattern>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
            return size() > maxSize;
          }
        });
  }

  public static boolean evaluate(Object target, String operatorStr, Object expected) {
    ConditionOperation conditionOperation = ConditionOperation.from(operatorStr);
    return evaluate(target, conditionOperation, expected);
  }

  public static boolean evaluate(
      Object target, ConditionOperation conditionOperation, Object expected) {
    return switch (conditionOperation) {
      case EQ -> Objects.equals(target, expected);
      case NE -> !Objects.equals(target, expected);
      case GT -> target != null && compareNumbers(target, expected) > 0;
      case GTE -> target != null && compareNumbers(target, expected) >= 0;
      case LT -> target != null && compareNumbers(target, expected) < 0;
      case LTE -> target != null && compareNumbers(target, expected) <= 0;
      case IN -> expected instanceof Collection<?> list && list.contains(target);
      case NIN -> expected instanceof Collection<?> list && !list.contains(target);
      case EXISTS -> target != null;
      case MISSING -> target == null;
      case CONTAINS -> contains(target, expected);
      case REGEX -> matchRegex(target, expected);
      case UNKNOWN -> false;
    };
  }

  /**
   * Compares two values numerically using BigDecimal for precision.
   *
   * <p><b>Type Conversion Rules:</b>
   *
   * <ul>
   *   <li>Numbers (Integer, Double, etc.) → Direct conversion to BigDecimal
   *   <li>Strings → Parsed as BigDecimal if valid numeric format
   *   <li>null values → Throws IllegalArgumentException (comparison impossible)
   *   <li>Non-numeric types → Throws IllegalArgumentException
   * </ul>
   *
   * <p><b>Examples:</b>
   *
   * <ul>
   *   <li>18 vs "18.0" → Equal (both converted to BigDecimal)
   *   <li>18 vs "18" → Equal
   *   <li>18.5 vs 18 → 18.5 is greater
   *   <li>null vs 18 → Throws exception
   *   <li>"abc" vs 18 → Throws exception
   * </ul>
   *
   * @param a first value to compare
   * @param b second value to compare
   * @return negative if a < b, zero if a == b, positive if a > b
   * @throws IllegalArgumentException if either value cannot be converted to a number
   */
  private static int compareNumbers(Object a, Object b) {
    try {
      BigDecimal left = toBigDecimal(a);
      BigDecimal right = toBigDecimal(b);
      return left.compareTo(right);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot compare non-numeric values: " + a + " vs " + b);
    }
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    if (value instanceof String str) {
      return new BigDecimal(str);
    }
    throw new IllegalArgumentException("Cannot convert to number: " + value);
  }

  private static boolean contains(Object target, Object expected) {
    if (target instanceof Collection<?> collection) {
      return collection.contains(expected);
    }
    if (target instanceof String str && expected instanceof String substr) {
      return str.contains(substr);
    }
    return false;
  }

  private static boolean matchRegex(Object target, Object expected) {
    if (target instanceof String str && expected instanceof String regex) {
      try {
        Pattern pattern = getCompiledPattern(regex);
        return pattern.matcher(str).matches();
      } catch (IllegalArgumentException e) {
        // Invalid regex pattern or security violation - return false for safety
        return false;
      }
    }
    return false;
  }

  /**
   * Get a compiled Pattern from cache or compile and cache it. Uses LRU eviction policy for optimal
   * cache performance. Includes safety checks for regex length.
   */
  private static Pattern getCompiledPattern(String regex) {
    // Safety check: prevent excessively long patterns
    if (regex.length() > MAX_REGEX_LENGTH) {
      throw new IllegalArgumentException(
          "Regex pattern too long: " + regex.length() + " > " + MAX_REGEX_LENGTH);
    }

    // Check cache first (LRU cache handles access order automatically)
    Pattern cached = PATTERN_CACHE.get(regex);
    if (cached != null) {
      return cached;
    }

    // Compile and cache the pattern (LRU eviction happens automatically)
    Pattern pattern = Pattern.compile(regex);
    PATTERN_CACHE.put(regex, pattern);
    return pattern;
  }
}
