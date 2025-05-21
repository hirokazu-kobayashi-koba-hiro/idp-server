package org.idp.server.core.oidc.authentication.evaluator;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionResults;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationResultCondition;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationResultConditions;

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
