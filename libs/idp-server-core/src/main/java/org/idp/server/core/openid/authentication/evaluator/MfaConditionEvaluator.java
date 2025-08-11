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

package org.idp.server.core.openid.authentication.evaluator;

import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationResultCondition;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationResultConditions;

public class MfaConditionEvaluator {

  public static boolean isSuccessSatisfied(
      AuthenticationResultConditions config, AuthenticationInteractionResults results) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    if (config.hasAllOf()) {
      for (AuthenticationResultCondition condition : config.allOf()) {
        if (!results.contains(condition.type())) return false;
        if (results.get(condition.type()).successCount() < condition.successCount()) return false;
      }
      return true;
    }

    if (config.hasAnyOf()) {
      for (AuthenticationResultCondition cond : config.anyOf()) {
        if (!results.contains(cond.type())) continue;
        if (results.get(cond.type()).successCount() >= cond.successCount()) return true;
      }
      return false;
    }

    return false;
  }

  public static boolean isFailureSatisfied(
      AuthenticationResultConditions config, AuthenticationInteractionResults results) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    if (results.containsDenyInteraction()) {
      return true;
    }

    if (config.hasAllOf()) {
      for (AuthenticationResultCondition condition : config.allOf()) {
        if (!results.contains(condition.type())) return false;
        if (results.get(condition.type()).failureCount() < condition.failureCount()) return false;
      }
      return true;
    }

    if (config.hasAnyOf()) {
      for (AuthenticationResultCondition cond : config.anyOf()) {
        if (!results.contains(cond.type())) continue;
        if (results.get(cond.type()).failureCount() >= cond.failureCount()) return true;
      }
      return false;
    }

    return false;
  }

  public static boolean isLockedSatisfied(
      AuthenticationResultConditions config, AuthenticationInteractionResults results) {
    if (!config.exists() || !results.exists()) {
      return false;
    }

    if (config.hasAllOf()) {
      for (AuthenticationResultCondition condition : config.allOf()) {
        if (!results.contains(condition.type())) return false;
        if (results.get(condition.type()).failureCount() < condition.failureCount()) return false;
      }
      return true;
    }

    if (config.hasAnyOf()) {
      for (AuthenticationResultCondition cond : config.anyOf()) {
        if (!results.contains(cond.type())) continue;
        if (results.get(cond.type()).failureCount() >= cond.failureCount()) return true;
      }
      return false;
    }

    return false;
  }
}
