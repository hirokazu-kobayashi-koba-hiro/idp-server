package org.idp.server.core.federation;

public interface FederationGateway {

  FederationTokenResponse requestToken(FederationTokenRequest federationTokenRequest);

  FederationJwksResponse getJwks(FederationJwksRequest federationJwksRequest);

  FederationUserinfoResponse requestUserInfo(FederationUserinfoRequest federationUserinfoRequest);

  FederationUserinfoResponse requestFacebookSpecificUerInfo(
      FederationUserinfoRequest federationUserinfoRequest);
}
