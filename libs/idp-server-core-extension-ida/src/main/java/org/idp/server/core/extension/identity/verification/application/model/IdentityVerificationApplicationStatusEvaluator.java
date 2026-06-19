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

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.configuration.process.*;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.condition.ConditionTransitionResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class IdentityVerificationApplicationStatusEvaluator {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationApplicationStatusEvaluator.class);

  /**
   * Evaluates the initial status when an application is created. The initial request may itself
   * transition (e.g. approve-on-apply); otherwise the application starts at the conventional
   * REQUESTED (the no-match candidate APPLYING is normalized down to it).
   */
  public static IdentityVerificationApplicationStatus evaluateInitial(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {
    IdentityVerificationApplicationStatus candidate = candidateOnProcess(config, context);
    return candidate == IdentityVerificationApplicationStatus.APPLYING
        ? IdentityVerificationApplicationStatus.REQUESTED
        : candidate;
  }

  /**
   * Evaluates the next status of a process attempt. An unsuccessful attempt (verification,
   * pre-hook, or execution error) records the attempt (failure_count) but does not move the
   * lifecycle. On success the candidate is computed statelessly from the transition config and
   * context, then reconciled against {@code currentStatus} so the result is stateful: terminal
   * states are absorbing and backward movement within the running phases is forbidden (#1617).
   */
  public static IdentityVerificationApplicationStatus evaluateOnProcess(
      IdentityVerificationApplicationStatus currentStatus,
      IdentityVerificationApplyingResult applyingResult,
      IdentityVerificationProcessConfiguration config) {
    if (!applyingResult.isSuccess()) {
      return currentStatus;
    }
    return reconcile(
        currentStatus, candidateOnProcess(config, applyingResult.applicationContext()));
  }

  /**
   * Evaluates the next status of a callback. See {@link #evaluateOnProcess} — same reconciliation,
   * with the callback no-match fallback (EXAMINATION_PROCESSING) as the candidate.
   *
   * <p><b>Contract</b>: unlike {@link #evaluateOnProcess} this has no failure branch — callers MUST
   * short-circuit on error before reaching here (e.g. {@code
   * IdentityVerificationCallbackEntryService} returns early on {@code applyingResult.isError()});
   * otherwise a failed callback could move the lifecycle. The asymmetry is intentional: only the
   * process path records failed attempts (failure_count) and therefore must run on failure.
   */
  public static IdentityVerificationApplicationStatus evaluateOnCallback(
      IdentityVerificationApplicationStatus currentStatus,
      IdentityVerificationContext context,
      IdentityVerificationProcessConfiguration config) {
    return reconcile(currentStatus, candidateOnCallback(config, context));
  }

  /**
   * Reconciles a freshly computed candidate against the current status (#1617):
   *
   * <ul>
   *   <li>terminal states are absorbing — once terminal, ignore further transitions
   *   <li>no backward movement within the running phases — the candidate falls back to a fixed
   *       running status when no transition condition matches, which must not rewind progress
   * </ul>
   *
   * <p>The invariants assume {@code current} is a running or terminal state. A neutral {@code
   * current} (UNDEFINED / UNKNOWN — only from persisted-data anomalies) matches neither guard, so
   * the candidate is accepted as-is (recovery).
   */
  static IdentityVerificationApplicationStatus reconcile(
      IdentityVerificationApplicationStatus current,
      IdentityVerificationApplicationStatus candidate) {
    if (current.isTerminal()) {
      return current;
    }
    if (candidate.isRunning()
        && current.isRunning()
        && candidate.runningRank() < current.runningRank()) {
      return current;
    }
    // current is a running/terminal state in normal flow; a neutral current (UNDEFINED / UNKNOWN)
    // matches neither guard above and is recovered by accepting the candidate as-is (see javadoc).
    return candidate;
  }

  private static IdentityVerificationApplicationStatus candidateOnProcess(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    log.debug("Status candidate evaluation (on process)");

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      log.info("Status transition: → APPROVED");
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      log.info("Status transition: → REJECTED");
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      log.info("Status transition: → CANCELLED");
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    IdentityVerificationConditionConfig appliedConfig = config.applied();
    ConditionTransitionResult appliedRequestResult = isAnySatisfied(appliedConfig, context.toMap());

    if (appliedRequestResult.isSuccess()) {
      log.info("Status transition: → APPLIED");
      return IdentityVerificationApplicationStatus.APPLIED;
    }

    log.debug(
        "Status remains: APPLYING, evaluated_conditions=[approved={}, rejected={}, "
            + "canceled={}, applied={}]",
        approvedRequestResult,
        rejectedRequestResult,
        cancellationRequestResult,
        appliedRequestResult);
    return IdentityVerificationApplicationStatus.APPLYING;
  }

  private static IdentityVerificationApplicationStatus candidateOnCallback(
      IdentityVerificationProcessConfiguration config, IdentityVerificationContext context) {

    log.debug("Status candidate evaluation (on callback)");

    IdentityVerificationConditionConfig approvedConfig = config.approved();
    ConditionTransitionResult approvedRequestResult =
        isAnySatisfied(approvedConfig, context.toMap());

    if (approvedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → APPROVED");
      return IdentityVerificationApplicationStatus.APPROVED;
    }

    IdentityVerificationConditionConfig rejectedConfig = config.rejected();
    ConditionTransitionResult rejectedRequestResult =
        isAnySatisfied(rejectedConfig, context.toMap());

    if (rejectedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → REJECTED");
      return IdentityVerificationApplicationStatus.REJECTED;
    }

    IdentityVerificationConditionConfig cancellationConfig = config.canceled();
    ConditionTransitionResult cancellationRequestResult =
        isAnySatisfied(cancellationConfig, context.toMap());

    if (cancellationRequestResult.isSuccess()) {
      log.info("Status transition (callback): → CANCELLED");
      return IdentityVerificationApplicationStatus.CANCELLED;
    }

    IdentityVerificationConditionConfig appliedConfig = config.applied();
    ConditionTransitionResult appliedRequestResult = isAnySatisfied(appliedConfig, context.toMap());

    if (appliedRequestResult.isSuccess()) {
      log.info("Status transition (callback): → APPLIED");
      return IdentityVerificationApplicationStatus.APPLIED;
    }

    log.debug(
        "Status remains: EXAMINATION_PROCESSING, evaluated_conditions=[approved={}, "
            + "rejected={}, canceled={}, applied={}]",
        approvedRequestResult,
        rejectedRequestResult,
        cancellationRequestResult,
        appliedRequestResult);
    return IdentityVerificationApplicationStatus.EXAMINATION_PROCESSING;
  }

  static ConditionTransitionResult isAnySatisfied(
      IdentityVerificationConditionConfig conditionConfig, Map<String, Object> request) {

    if (!conditionConfig.exists()) {
      return ConditionTransitionResult.UNDEFINED;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(request);
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());

    for (List<IdentityVerificationCondition> resultConditions : conditionConfig.anyOf()) {

      if (isAllSatisfied(resultConditions, jsonPathWrapper)) {
        return ConditionTransitionResult.SUCCESS;
      }
    }

    return ConditionTransitionResult.FAILURE;
  }

  static boolean isAllSatisfied(
      List<IdentityVerificationCondition> resultConditions, JsonPathWrapper jsonPathWrapper) {
    log.debug("Evaluating AND conditions: count={}", resultConditions.size());

    for (int i = 0; i < resultConditions.size(); i++) {
      IdentityVerificationCondition resultCondition = resultConditions.get(i);

      Object actualValue = jsonPathWrapper.readRaw(resultCondition.path());
      boolean result =
          ConditionOperationEvaluator.evaluate(
              actualValue, resultCondition.operation(), resultCondition.value());

      log.debug(
          "Condition[{}]: path={}, operation={}, expected={}, actual={}, result={}",
          i,
          resultCondition.path(),
          resultCondition.operation(),
          maskSensitiveValue(resultCondition.value()),
          maskSensitiveValue(actualValue),
          result);

      if (!result) {
        log.debug(
            "AND condition group failed at index {}: path={}, expected={}, actual={}",
            i,
            resultCondition.path(),
            maskSensitiveValue(resultCondition.value()),
            maskSensitiveValue(actualValue));
        return false;
      }
    }

    log.debug("All AND conditions satisfied: count={}", resultConditions.size());
    return true;
  }

  private static Object maskSensitiveValue(Object value) {
    if (value instanceof String str) {
      if (str.contains("token") || str.contains("password") || str.length() > 50) {
        return "***";
      }
    }
    return value;
  }
}
