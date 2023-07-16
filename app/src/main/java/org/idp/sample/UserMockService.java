package org.idp.sample;

import jakarta.servlet.http.HttpSession;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.Address;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.identity.VerifiableCredential;
import org.idp.server.oauth.interaction.UserInteraction;
import org.idp.server.token.PasswordCredentialsGrantDelegate;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.Password;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oauth.Username;
import org.springframework.stereotype.Service;

@Service
public class UserMockService
    implements CibaRequestDelegate, PasswordCredentialsGrantDelegate, OAuthRequestDelegate {
  HttpSession httpSession;

  public UserMockService(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public User find(TokenIssuer tokenIssuer, UserCriteria criteria) {

    if (!"001".equals(criteria.loginHint().value())) {
      return new User();
    }
    return getUser();
  }

  @Override
  public boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode) {

    return "successUserCode".equals(userCode.value());
  }

  @Override
  public CustomProperties getCustomProperties(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {

    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public void notify(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {}

  @Override
  public User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password) {
    if (!"001".equals(username.value())) {
      return new User();
    }
    if (!"successUserCode".equals(password.value())) {
      return new User();
    }
    return getUser();
  }

  @Override
  public CustomProperties getCustomProperties(TokenIssuer tokenIssuer, User user) {
    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public OAuthSession findSession(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    return (OAuthSession) httpSession.getAttribute(sessionKey);
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    String id = httpSession.getId();
    httpSession.getId();
    httpSession.setAttribute(sessionKey, oAuthSession);
    System.out.println(id);
  }

  public User getUser() {
    VerifiableCredential verifiableCredential = getCredential();
    return new User()
        .setSub("001")
        .setName("ito ichiro")
        .setGivenName("ichiro")
        .setMiddleName("mac")
        .setFamilyName("ito")
        .setNickname("ito")
        .setPreferredUsername("ichiro")
        .setProfile("https://example.com/profiles/123")
        .setPicture("https://example.com/pictures/123")
        .setWebsite("https://example.com")
        .setEmail("ito.ichiro@gmail.com")
        .setEmailVerified(true)
        .setGender("other")
        .setBirthdate("2000-02-02")
        .setZoneinfo("ja-jp")
        .setLocale("locale")
        .setPhoneNumber("09012345678")
        .setPhoneNumberVerified(false)
        .setAddress(new Address().setStreetAddress("street"))
        .setUpdatedAt(SystemDateTime.now().toEpochSecond(ZoneOffset.UTC))
        .setVerifiableCredentials(List.of(verifiableCredential));
  }

  public VerifiableCredential getCredential() {
    String vc =
        """
                {
                    "@context": [
                        "https://www.w3.org/2018/credentials/v1",
                        "https://www.w3.org/2018/credentials/examples/v1"
                    ],
                    "id": "http://example.edu/credentials/1872",
                    "type": [
                        "VerifiableCredential",
                        "AlumniCredential"
                    ],
                    "issuer": "https://example.edu/issuers/565049",
                    "issuanceDate": "2010-01-01T19:23:24Z",
                    "credentialSubject": {
                        "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
                        "alumniOf": {
                            "id": "did:example:c276e12ec21ebfeb1f712ebc6f1",
                            "name": [
                                {
                                    "value": "Example University",
                                    "lang": "en"
                                },
                                {
                                    "value": "Exemple d'Universite",
                                    "lang": "fr"
                                }
                            ]
                        }
                    },
                    "proof": {
                        "type": "RsaSignature2018",
                        "created": "2017-06-18T21:19:10Z",
                        "proofPurpose": "assertionMethod",
                        "verificationMethod": "https://example.edu/issuers/565049#key-1",
                        "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5XsITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUcX16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtjPAYuNzVBAh4vGHSrQyHUdBBPM"
                    }
                }
                """;
    return VerifiableCredential.parse(vc);
  }

  public UserInteraction getUserInteraction(String sessionKey, String username, String password) {
    OAuthSession session = (OAuthSession) httpSession.getAttribute(sessionKey);
    if (Objects.nonNull(session) && password.isEmpty()) {
      Authentication authentication = session.authentication();
      return new UserInteraction(getUser(), authentication);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .setMethods(List.of("password"))
            .setAcrValues(List.of("urn:mace:incommon:iap:silver"));
    return new UserInteraction(getUser(), authentication);
  }
}
