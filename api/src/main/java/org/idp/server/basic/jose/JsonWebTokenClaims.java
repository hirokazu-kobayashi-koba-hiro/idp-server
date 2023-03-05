package org.idp.server.basic.jose;

import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.idp.server.core.type.OAuthRequestKey;

/** JsonWebTokenClaims */
public class JsonWebTokenClaims {
  JWTClaimsSet value;

  public JsonWebTokenClaims() {}

  public JsonWebTokenClaims(JWTClaimsSet value) {
    this.value = value;
  }

  public String getIss() {
    return value.getIssuer();
  }

  public boolean hasIss() {
    return contains(JWTClaimNames.ISSUER);
  }

  public String getSub() {
    return value.getSubject();
  }

  public boolean hasSub() {
    return contains(JWTClaimNames.SUBJECT);
  }

  public List<String> getAud() {
    return value.getAudience();
  }

  public boolean hasAud() {
    return contains(JWTClaimNames.AUDIENCE);
  }

  public Date getNbf() {
    return value.getNotBeforeTime();
  }

  public boolean hasNbf() {
    return contains(JWTClaimNames.NOT_BEFORE);
  }

  public Date getIat() {
    return value.getIssueTime();
  }

  public boolean hasIat() {
    return contains(JWTClaimNames.ISSUED_AT);
  }

  public String getJti() {
    return value.getJWTID();
  }

  public boolean hasJti() {
    return contains(JWTClaimNames.JWT_ID);
  }

  public Map<String, Object> payload() {
    return value.getClaims();
  }

  public String scope() {
    return getValue(OAuthRequestKey.scope);
  }

  String getValue(OAuthRequestKey key) {
    if (contains(key.name())) {
      return "";
    }
    return (String) payload().get(key.name());
  }

  boolean contains(String key) {
    return value.getClaims().containsKey(key);
  }
}
