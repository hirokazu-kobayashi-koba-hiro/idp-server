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

package org.idp.server.core.extension.identity.verification.application.model;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
public class OperatorEvaluator {

  public static boolean evaluate(Object target, String operatorStr, Object expected) {
    Operator operator = Operator.from(operatorStr);
    return evaluate(target, operator, expected);
  }

  public static boolean evaluate(Object target, Operator operator, Object expected) {
    switch (operator) {
      case EQ:
        return Objects.equals(target, expected);
      case NE:
        return !Objects.equals(target, expected);
      case GT:
        return compareNumbers(target, expected) > 0;
      case GTE:
        return compareNumbers(target, expected) >= 0;
      case LT:
        return compareNumbers(target, expected) < 0;
      case LTE:
        return compareNumbers(target, expected) <= 0;
      case IN:
        return expected instanceof Collection<?> list && list.contains(target);
      case NIN:
        return expected instanceof Collection<?> list && !list.contains(target);
      case EXISTS:
        return target != null;
      case MISSING:
        return target == null;
      case CONTAINS:
        return contains(target, expected);
      case REGEX:
        return matchRegex(target, expected);
      default:
        throw new UnsupportedOperationException("Unsupported operator: " + operator);
    }
  }

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
      return Pattern.matches(regex, str);
    }
    return false;
  }
}
