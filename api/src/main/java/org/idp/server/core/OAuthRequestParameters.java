package org.idp.server.core;


/** OAuthRequestParameters */
public class OAuthRequestParameters {
  MultiValueMap map;

  public OAuthRequestParameters() {
    this.map = new MultiValueMap();
  }

  public OAuthRequestParameters(MultiValueMap map) {
    this.map = map;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }


  String getString(OAuthRequestKey key) {
    if (map.contains(key.name())) {
      return "";
    }
    return map.getFirst(key.name());
  }
}
