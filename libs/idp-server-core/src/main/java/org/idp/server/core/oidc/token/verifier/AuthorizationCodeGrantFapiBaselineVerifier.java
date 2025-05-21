package org.idp.server.core.oidc.token.verifier;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.core.oidc.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.oidc.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class AuthorizationCodeGrantFapiBaselineVerifier
    implements AuthorizationCodeGrantVerifierInterface {

  AuthorizationCodeGrantBaseVerifier baseVerifier = new AuthorizationCodeGrantBaseVerifier();

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);
    throwExceptionIfInvalidSymmetricKey(tokenRequestContext);
    throwExceptionIfClientSecretPostOrClientSecretBasic(tokenRequestContext);
    throwExceptionIfRsaAlgorithmKeySizeSmallerThan2048(tokenRequestContext, clientCredentials);
    throwExceptionIfEllipticCurveAlgorithmsSmallerThan160(tokenRequestContext, clientCredentials);
    throwExceptionIfMisMatchedClientId(tokenRequestContext, clientCredentials);
  }

  /**
   * shall provide a client secret that adheres to the requirements in Section 16.19 of OIDC if a
   * symmetric key is used;
   *
   * <p>16.19. Symmetric Key Entropy
   *
   * <p>In Section 10.1 and Section 10.2, keys are derived from the client_secret value. Thus, when
   * used with symmetric signing or encryption operations, client_secret values MUST contain
   * sufficient entropy to generate cryptographically strong keys. Also, client_secret values MUST
   * also contain at least the minimum of number of octets required for MAC keys for the particular
   * algorithm used. So for instance, for HS256, the client_secret value MUST contain at least 32
   * octets (and almost certainly SHOULD contain more, since client_secret values are likely to use
   * a restricted alphabet).
   */
  void throwExceptionIfInvalidSymmetricKey(TokenRequestContext tokenRequestContext) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (!clientAuthenticationType.isClientSecretJwt()) {
      return;
    }
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();
    ClientSecret clientSecret = clientConfiguration.clientSecret();
    if (clientSecret.octetsSize() < 32) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, the client_secret value MUST contain at least 32 octets");
    }
  }

  /**
   * shall authenticate the confidential client using one of the following methods: Mutual TLS for
   * OAuth Client Authentication as specified in Section 2 of MTLS, or client_secret_jwt or
   * private_key_jwt as specified in Section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasic(
      TokenRequestContext tokenRequestContext) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_basic MUST not used");
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_post MUST not used");
    }
  }

  /** shall require and use a key of size 2048 bits or larger for RSA algorithms; */
  void throwExceptionIfRsaAlgorithmKeySizeSmallerThan2048(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (!clientAuthenticationType.isPrivateKeyJwt()) {
      return;
    }
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        clientCredentials.clientAuthenticationPublicKey();
    if (!clientAuthenticationPublicKey.isRsa()) {
      return;
    }
    if (clientAuthenticationPublicKey.size() < 2048) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, shall require and use a key of size 2048 bits or larger for RSA algorithms");
    }
  }

  /** shall require and use a key of size 160 bits or larger for elliptic curve algorithms; */
  void throwExceptionIfEllipticCurveAlgorithmsSmallerThan160(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (!clientAuthenticationType.isPrivateKeyJwt()) {
      return;
    }
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        clientCredentials.clientAuthenticationPublicKey();
    if (!clientAuthenticationPublicKey.isEc()) {
      return;
    }
    if (clientAuthenticationPublicKey.size() < 160) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, shall require and use a key of size 160 bits or larger for elliptic curve algorithms");
    }
  }

  /**
   * shall return an invalid_client error as defined in 5.2 of RFC6749 when mis-matched client
   * identifiers were provided through the client authentication methods that permits sending the
   * client identifier in more than one way;
   */
  void throwExceptionIfMisMatchedClientId(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (!clientAuthenticationType.isClientSecretJwt()
        && !clientAuthenticationType.isPrivateKeyJwt()) {
      return;
    }
    ClientAssertionJwt clientAssertionJwt = clientCredentials.clientAssertionJwt();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();
    if (!clientAssertionJwt.subject().equals(clientConfiguration.clientIdValue())) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI Baseline profile, client_id must matched client_assertion sub claim (%s) (%s))",
              clientConfiguration.clientIdValue(), clientAssertionJwt.subject()));
    }
    if (!clientAssertionJwt.iss().equals(clientConfiguration.clientIdValue())) {
      throw new TokenBadRequestException(
          "invalid_client",
          String.format(
              "When FAPI Baseline profile, client_id must matched client_assertion iss claim (%s) (%s))",
              clientConfiguration.clientIdValue(), clientAssertionJwt.iss()));
    }
  }
}
