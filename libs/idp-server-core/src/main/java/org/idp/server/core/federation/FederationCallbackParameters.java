package org.idp.server.core.federation;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.oauth.*;

/** FederationCallbackParameters */
public class FederationCallbackParameters {
  ArrayValueMap values;

  public FederationCallbackParameters() {
    this.values = new ArrayValueMap();
  }

  public FederationCallbackParameters(ArrayValueMap values) {
    this.values = values;
  }

  public FederationCallbackParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public State state() {
    return new State(getValueOrEmpty("state"));
  }

  public boolean hasState() {
    return contains("state");
  }

  public String getValueOrEmpty(String key) {
    return values.getFirstOrEmpty(key);
  }

  boolean contains(String key) {
    return values.contains(key);
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(getValueOrEmpty("iss"));
  }

  public boolean hasTokenIssuer() {
    return contains("iss");
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  public String code() {
    return getValueOrEmpty("code");
  }
}
