package org.idp.server.core.federation;

public interface FederationGateway {

  FederationTokenResponse requestToken(FederationTokenRequest federationTokenRequest);

  FederationUserinfoResponse requestUserInfo(FederationUserinfoRequest federationUserinfoRequest);
}
