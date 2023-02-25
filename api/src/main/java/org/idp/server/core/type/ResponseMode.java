package org.idp.server.core.type;

/** ResponseMode */
public enum ResponseMode {
  query("query"),
  fragment("fragment"),
  form_post("form_post"),
  query_jwt("query.jwt"),
  fragment_jwt("fragment.jwt"),
  form_post_jwt("form_post.jwt"),
  jwt("jwt");

  String value;

  ResponseMode(String value) {
    this.value = value;
  }
}
