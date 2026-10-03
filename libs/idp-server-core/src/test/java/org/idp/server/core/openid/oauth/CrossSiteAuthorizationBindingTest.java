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

package org.idp.server.core.openid.oauth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthSessionId;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.AuthenticationInteractionStatus;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationInteractor;
import org.idp.server.core.openid.authentication.AuthenticationInteractors;
import org.idp.server.core.openid.authentication.AuthenticationRequest;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
import org.idp.server.core.openid.authentication.OperationType;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rules that bind an authorization to the browser where the authorization view is on another
 * site, exercised without any repository, cookie or session store.
 */
class CrossSiteAuthorizationBindingTest {

  private static final AesCipher CIPHER =
      new AesCipher(Base64.getEncoder().encodeToString(new byte[32]));
  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  private static final String PASSWORD = "password-authentication";
  private static final String FIDO_UAF = "fido-uaf-authentication";
  private static final String SUB = "5b1f0c92-3e7a-4d18-9c60-2a8e4f7d1b39";
  private static final AuthSessionId STARTED_WITH = new AuthSessionId("auth-session-of-browser-a");
  private static final RedirectUri ENTITLED = new RedirectUri("https://rp.example.com/callback");
  private static final String TARGET = "https://rp.example.com/callback?code=abc&state=xyz";

  private final CrossSiteAuthorizationBinding crossSite =
      new CrossSiteAuthorizationBinding(true, CIPHER);
  private final CrossSiteAuthorizationBinding sameSite =
      new CrossSiteAuthorizationBinding(false, CIPHER);

  private final AuthenticationInteractors interactors =
      new AuthenticationInteractors(
          Map.of(
              new AuthenticationInteractionType(PASSWORD), interactor(PASSWORD, true),
              new AuthenticationInteractionType(FIDO_UAF), interactor(FIDO_UAF, false)));

  @Nested
  @DisplayName("認可画面に渡す値（view binding）")
  class ViewBinding {

    @Test
    @DisplayName("発行した値を出せば、始めたブラウザからの呼び出しとして通る")
    void issuedValuePasses() {
      CrossSiteAuthorizationBinding.ViewBinding issued =
          crossSite.issueViewBinding(transaction(Map.of(), STARTED_WITH));

      assertDoesNotThrow(
          () -> crossSite.verifyViewCall(issued.transaction(), null, issued.value()));
    }

    @Test
    @DisplayName("値が無い・違う呼び出しは通さない")
    void missingOrWrongValueRejected() {
      CrossSiteAuthorizationBinding.ViewBinding issued =
          crossSite.issueViewBinding(transaction(Map.of(), STARTED_WITH));

      assertThrows(
          UnauthorizedException.class,
          () -> crossSite.verifyViewCall(issued.transaction(), null, null));
      assertThrows(
          UnauthorizedException.class,
          () -> crossSite.verifyViewCall(issued.transaction(), null, ""));
      assertThrows(
          UnauthorizedException.class,
          () -> crossSite.verifyViewCall(issued.transaction(), null, "not-the-value"));
    }

    @Test
    @DisplayName("値を発行していないトランザクションは通さない")
    void transactionWithoutValueRejected() {
      assertThrows(
          UnauthorizedException.class,
          () -> crossSite.verifyViewCall(transaction(Map.of(), STARTED_WITH), null, "anything"));
    }

    @Test
    @DisplayName("発行し直すと、前の値は通らない")
    void reissuedValueReplacesEarlier() {
      AuthenticationTransaction transaction = transaction(Map.of(), STARTED_WITH);
      CrossSiteAuthorizationBinding.ViewBinding first = crossSite.issueViewBinding(transaction);
      CrossSiteAuthorizationBinding.ViewBinding second =
          crossSite.issueViewBinding(first.transaction());

      assertNotEquals(first.value(), second.value());
      assertThrows(
          UnauthorizedException.class,
          () -> crossSite.verifyViewCall(second.transaction(), null, first.value()));
      assertDoesNotThrow(
          () -> crossSite.verifyViewCall(second.transaction(), null, second.value()));
    }

    @Test
    @DisplayName("値そのものはトランザクションに残さない（ハッシュだけ）")
    void onlyHashIsStored() {
      CrossSiteAuthorizationBinding.ViewBinding issued =
          crossSite.issueViewBinding(transaction(Map.of(), STARTED_WITH));

      assertFalse(issued.transaction().attributes().toMap().toString().contains(issued.value()));
    }

    @Test
    @DisplayName("同一サイト構成では発行せず、Cookie で照合する（Cookie の照合のエラーのまま）")
    void sameSiteUsesCookie() {
      AuthenticationTransaction transaction = transaction(Map.of(), STARTED_WITH);

      assertNull(sameSite.issueViewBinding(transaction));
      assertDoesNotThrow(() -> sameSite.verifyViewCall(transaction, STARTED_WITH, null));
      UnauthorizedException missing =
          assertThrows(
              UnauthorizedException.class, () -> sameSite.verifyViewCall(transaction, null, null));
      assertTrue(missing.getMessage().startsWith("auth_session_mismatch"));
      assertThrows(
          UnauthorizedException.class,
          () ->
              sameSite.verifyViewCall(
                  transaction, new AuthSessionId("auth-session-of-browser-b"), null));
    }

    @Test
    @DisplayName("別サイト構成の拒否は view_binding_mismatch")
    void crossSiteRejectionNamesViewBinding() {
      UnauthorizedException rejected =
          assertThrows(
              UnauthorizedException.class,
              () ->
                  crossSite.verifyViewCall(
                      transaction(Map.of(), STARTED_WITH), STARTED_WITH, null));
      assertTrue(rejected.getMessage().startsWith("view_binding_mismatch"));
    }

    @Test
    @DisplayName("ポリシーで束縛を外していれば通す（opt-out を尊重する）")
    void optOutHonoured() {
      AuthenticationTransaction transaction =
          transaction(
              Map.of(),
              STARTED_WITH,
              JSON.read("{\"auth_session_binding_required\": false}", AuthenticationPolicy.class));

      assertDoesNotThrow(() -> crossSite.verifyViewCall(transaction, null, null));
    }
  }

  @Nested
  @DisplayName("認証ステップの proof")
  class ProofForStep {

    @Test
    @DisplayName("ブラウザでの本人しか通せないステップが成功すると発行する")
    void browserStepEarnsProof() {
      AuthenticationProof proof =
          crossSite.proofForStep(
              interactor(PASSWORD, true), stepResult(OperationType.AUTHENTICATION, true, user()));

      assertNotNull(proof);
      assertNotNull(proof.value());
    }

    @Test
    @DisplayName("同一サイト構成では発行しない")
    void sameSiteIssuesNone() {
      assertNull(
          sameSite.proofForStep(
              interactor(PASSWORD, true), stepResult(OperationType.AUTHENTICATION, true, user())));
    }

    @Test
    @DisplayName("デバイスからの呼び出しには発行しない（ブラウザに届かない）")
    void deviceStepIssuesNone() {
      assertNull(
          crossSite.proofForStep(
              interactor(FIDO_UAF, false), stepResult(OperationType.AUTHENTICATION, true, user())));
    }

    @Test
    @DisplayName("challenge のように誰でも通せるステップには発行しない")
    void challengeIssuesNone() {
      assertNull(
          crossSite.proofForStep(
              interactor(PASSWORD, true), stepResult(OperationType.CHALLENGE, true, user())));
    }

    @Test
    @DisplayName("失敗したステップには発行しない")
    void failureIssuesNone() {
      assertNull(
          crossSite.proofForStep(
              interactor(PASSWORD, true), stepResult(OperationType.AUTHENTICATION, false, user())));
    }

    @Test
    @DisplayName("ユーザーが決まっていなければ発行しない")
    void noUserIssuesNone() {
      assertNull(
          crossSite.proofForStep(
              interactor(PASSWORD, true), stepResult(OperationType.AUTHENTICATION, true, null)));
    }
  }

  @Nested
  @DisplayName("トランザクションへの持ち運び")
  class Carry {

    @Test
    @DisplayName("作ったセッションと proof を書く")
    void carriesSessionAndProof() {
      AuthenticationProof proof = crossSite.proofForFederation(user());
      AuthenticationTransaction carried =
          crossSite.carry(transaction(Map.of(), STARTED_WITH), "op-session-1", proof);

      assertNotNull(carried);
      assertEquals("op-session-1", crossSite.boundSessionId(carried));
      assertTrue(carried.attributes().containsKey("auth_proof"));
    }

    @Test
    @DisplayName("何も無ければ書かない")
    void nothingToCarry() {
      assertNull(crossSite.carry(transaction(Map.of(), STARTED_WITH), null, null));
    }

    @Test
    @DisplayName("同一サイト構成では書かない")
    void sameSiteCarriesNothing() {
      assertNull(sameSite.carry(transaction(Map.of(), STARTED_WITH), "op-session-1", null));
    }

    @Test
    @DisplayName("認可リクエストで見つけたセッションを紐づける")
    void bindsSessionFoundAtRequest() {
      AuthenticationTransaction bound =
          crossSite.bindSession(transaction(Map.of(), STARTED_WITH), "op-session-2");

      assertEquals("op-session-2", crossSite.boundSessionId(bound));
      assertNull(sameSite.boundSessionId(bound));
    }
  }

  @Nested
  @DisplayName("authorize の関門")
  class GateAuthorize {

    @Test
    @DisplayName("同一サイト構成は Cookie の照合に回す")
    void sameSiteChecksCookie() {
      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.CHECK_BROWSER_COOKIE,
          sameSite.gateAuthorize(browserAuthenticated(), interactors, null));
    }

    @Test
    @DisplayName("ブラウザで認証していれば、そのとき渡した proof で通る")
    void earnedProofPasses() {
      AuthenticationTransaction transaction = browserAuthenticated();
      AuthenticationProof proof = crossSite.proofForFederation(user());
      AuthenticationTransaction carried = crossSite.carry(transaction, null, proof);

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.PROCEED,
          crossSite.gateAuthorize(carried, interactors, proof.value()));
    }

    @Test
    @DisplayName("ブラウザで認証していて proof が無ければ拒否する")
    void missingProofRejected() {
      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.PROOF_REJECTED,
          crossSite.gateAuthorize(browserAuthenticated(), interactors, null));
    }

    @Test
    @DisplayName("デバイスだけで認証していれば proof を求めない（/complete で束縛する）")
    void deviceOnlyProceeds() {
      AuthenticationTransaction transaction =
          transaction(Map.of(FIDO_UAF, result("AUTHENTICATION", 1)), STARTED_WITH);

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.PROCEED,
          crossSite.gateAuthorize(transaction, interactors, null));
    }

    @Test
    @DisplayName("/complete 用の proof を渡したあとは、もう一度通さない")
    void handedOffRejectsAgain() {
      AuthenticationTransaction handedOff =
          crossSite.handOff(browserAuthenticated(), user(), TARGET).transaction();

      assertTrue(crossSite.handedOffForCompletion(handedOff));
      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.ALREADY_HANDED_OFF,
          crossSite.gateAuthorize(handedOff, interactors, null));
    }
  }

  @Nested
  @DisplayName("authorize-with-session の関門")
  class GateAuthorizeWithSession {

    @Test
    @DisplayName("同一サイト構成は Cookie の照合に回す")
    void sameSiteChecksCookie() {
      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.CHECK_BROWSER_COOKIE,
          sameSite.gateAuthorizeWithSession(browserAuthenticated()));
    }

    @Test
    @DisplayName("認可リクエストで紐づけたセッションだけなら続けられる（SSO）")
    void sessionBoundAtRequestProceeds() {
      AuthenticationTransaction bound =
          crossSite.bindSession(transaction(Map.of(), STARTED_WITH), "op-session-of-starter");

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.PROCEED,
          crossSite.gateAuthorizeWithSession(bound));
    }

    @Test
    @DisplayName("この認可でサインインが成功していたら拒否する（そのセッションは呼び出し元を示さない）")
    void signedInDuringFlowRejected() {
      AuthenticationTransaction carried =
          crossSite.carry(browserAuthenticated(), "op-session-of-whoever-signed-in", null);

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.SIGNED_IN_DURING_FLOW,
          crossSite.gateAuthorizeWithSession(carried));
    }

    @Test
    @DisplayName("デバイスでのサインインでも拒否する")
    void deviceSignInRejected() {
      AuthenticationTransaction transaction =
          transaction(Map.of(FIDO_UAF, result("AUTHENTICATION", 1)), STARTED_WITH);

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.SIGNED_IN_DURING_FLOW,
          crossSite.gateAuthorizeWithSession(transaction));
    }

    @Test
    @DisplayName("失敗しただけなら続けられる")
    void failedAttemptOnlyProceeds() {
      AuthenticationTransaction transaction =
          transaction(Map.of(PASSWORD, result("AUTHENTICATION", 0)), STARTED_WITH);

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.PROCEED,
          crossSite.gateAuthorizeWithSession(transaction));
    }

    @Test
    @DisplayName("/complete 用の proof を渡したあとは通さない")
    void handedOffRejected() {
      AuthenticationTransaction handedOff =
          crossSite
              .handOff(
                  crossSite.bindSession(transaction(Map.of(), STARTED_WITH), "op-session"),
                  user(),
                  TARGET)
              .transaction();

      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.ALREADY_HANDED_OFF,
          crossSite.gateAuthorizeWithSession(handedOff));
    }
  }

  @Nested
  @DisplayName("/complete への受け渡し")
  class HandOff {

    @Test
    @DisplayName("authorize 用の proof を消費し、/complete 用の proof を置く")
    void spendsFirstAndStoresSecond() {
      AuthenticationProof first = crossSite.proofForFederation(user());
      AuthenticationTransaction carried = crossSite.carry(browserAuthenticated(), null, first);

      CrossSiteAuthorizationBinding.HandOff handOff = crossSite.handOff(carried, user(), TARGET);

      assertNotNull(handOff.proofValue());
      assertEquals(
          CrossSiteAuthorizationBinding.AuthorizeGate.ALREADY_HANDED_OFF,
          crossSite.gateAuthorize(handOff.transaction(), interactors, first.value()));
      assertTrue(
          crossSite
              .checkCompletion(handOff.transaction(), STARTED_WITH, handOff.proofValue(), ENTITLED)
              .isAccepted());
    }

    @Test
    @DisplayName("同一サイト構成や遷移先の無い応答では渡さない")
    void nothingToHandOff() {
      assertNull(sameSite.handOff(browserAuthenticated(), user(), TARGET));
      assertNull(crossSite.handOff(browserAuthenticated(), user(), ""));
    }
  }

  @Nested
  @DisplayName("/complete の照合")
  class CheckCompletion {

    @Test
    @DisplayName("始めたブラウザが、渡された proof を出せば、proof の中の遷移先へ")
    void accepted() {
      CrossSiteAuthorizationBinding.HandOff handOff = handedOff(STARTED_WITH, null);

      CrossSiteAuthorizationBinding.CompletionCheck check =
          crossSite.checkCompletion(
              handOff.transaction(), STARTED_WITH, handOff.proofValue(), ENTITLED);

      assertTrue(check.isAccepted());
      assertEquals(TARGET, check.redirectUri().value());
    }

    @Test
    @DisplayName("トランザクションに束縛が無ければ拒否する（照合するものが無い、とは扱わない）")
    void noBindingRejected() {
      CrossSiteAuthorizationBinding.HandOff handOff = handedOff(null, null);

      CrossSiteAuthorizationBinding.CompletionCheck check =
          crossSite.checkCompletion(
              handOff.transaction(), STARTED_WITH, handOff.proofValue(), ENTITLED);

      assertFalse(check.isAccepted());
      assertEquals(
          "this browser did not start the authorization request.", check.errorDescription());
    }

    @Test
    @DisplayName("始めたブラウザでなければ、proof が正しくても拒否する")
    void otherBrowserRejected() {
      CrossSiteAuthorizationBinding.HandOff handOff = handedOff(STARTED_WITH, null);

      CrossSiteAuthorizationBinding.CompletionCheck check =
          crossSite.checkCompletion(
              handOff.transaction(), new AuthSessionId(), handOff.proofValue(), ENTITLED);

      assertFalse(check.isAccepted());
      assertEquals(
          "this browser did not start the authorization request.", check.errorDescription());
    }

    @Test
    @DisplayName("ポリシーで束縛を外していれば、始めたブラウザ以外でも通る（opt-out を尊重する）")
    void optOutHonoured() {
      CrossSiteAuthorizationBinding.HandOff handOff =
          handedOff(
              STARTED_WITH,
              JSON.read("{\"auth_session_binding_required\": false}", AuthenticationPolicy.class));

      assertTrue(
          crossSite
              .checkCompletion(
                  handOff.transaction(), new AuthSessionId(), handOff.proofValue(), ENTITLED)
              .isAccepted());
    }

    @Test
    @DisplayName("authorize 用の proof は受け取らない")
    void firstStageProofRejected() {
      AuthenticationProof first = crossSite.proofForFederation(user());
      AuthenticationTransaction carried = crossSite.carry(browserAuthenticated(), null, first);

      CrossSiteAuthorizationBinding.CompletionCheck check =
          crossSite.checkCompletion(carried, STARTED_WITH, first.value(), ENTITLED);

      assertFalse(check.isAccepted());
      assertEquals("authorization request is not in progress.", check.errorDescription());
    }

    @Test
    @DisplayName("proof の中の遷移先が、リクエストの redirect_uri と違えば拒否する")
    void redirectMismatchRejected() {
      CrossSiteAuthorizationBinding.HandOff handOff = handedOff(STARTED_WITH, null);

      CrossSiteAuthorizationBinding.CompletionCheck check =
          crossSite.checkCompletion(
              handOff.transaction(),
              STARTED_WITH,
              handOff.proofValue(),
              new RedirectUri("https://other.example.com/callback"));

      assertFalse(check.isAccepted());
      assertEquals(
          "redirect target does not match the authorization request.", check.errorDescription());
    }
  }

  private CrossSiteAuthorizationBinding.HandOff handedOff(
      AuthSessionId startedWith, AuthenticationPolicy policy) {
    AuthenticationTransaction transaction =
        transaction(Map.of(PASSWORD, result("AUTHENTICATION", 1)), startedWith, policy);
    return crossSite.handOff(transaction, user(), TARGET);
  }

  private AuthenticationTransaction browserAuthenticated() {
    return transaction(Map.of(PASSWORD, result("AUTHENTICATION", 1)), STARTED_WITH);
  }

  private static User user() {
    return new User().setSub(SUB);
  }

  private static AuthenticationTransaction transaction(
      Map<String, AuthenticationInteractionResult> results, AuthSessionId startedWith) {
    return transaction(results, startedWith, null);
  }

  private static AuthenticationTransaction transaction(
      Map<String, AuthenticationInteractionResult> results,
      AuthSessionId startedWith,
      AuthenticationPolicy policy) {
    AuthenticationRequest request =
        new AuthenticationRequest(
            null,
            null,
            null,
            new RequestedClientId("client"),
            null,
            user(),
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(10));
    AuthenticationTransactionAttributes attributes =
        startedWith != null
            ? AuthenticationTransactionAttributes.withAuthSessionId(startedWith)
            : new AuthenticationTransactionAttributes();
    return new AuthenticationTransaction(
        new AuthenticationTransactionIdentifier("tx"),
        new AuthorizationIdentifier("auth"),
        request,
        policy,
        new AuthenticationInteractionResults(new HashMap<>(results)),
        attributes);
  }

  private static AuthenticationInteractionResult result(String operationType, int successCount) {
    return new AuthenticationInteractionResult(
        operationType, "test", 1, successCount, 1 - successCount, LocalDateTime.now());
  }

  private static AuthenticationInteractionRequestResult stepResult(
      OperationType operationType, boolean success, User user) {
    return new AuthenticationInteractionRequestResult(
        success
            ? AuthenticationInteractionStatus.SUCCESS
            : AuthenticationInteractionStatus.CLIENT_ERROR,
        new AuthenticationInteractionType(PASSWORD),
        operationType,
        "pwd",
        user,
        Map.of(),
        DefaultSecurityEventType.password_success);
  }

  private static AuthenticationInteractor interactor(String type, boolean browserBased) {
    return new AuthenticationInteractor() {
      @Override
      public AuthenticationInteractionType type() {
        return new AuthenticationInteractionType(type);
      }

      @Override
      public boolean isBrowserBased() {
        return browserBased;
      }

      @Override
      public String method() {
        return type;
      }

      @Override
      public AuthenticationInteractionRequestResult interact(
          Tenant tenant,
          AuthenticationTransaction transaction,
          AuthenticationInteractionType interactionType,
          AuthenticationInteractionRequest request,
          RequestAttributes requestAttributes,
          UserQueryRepository userQueryRepository) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
