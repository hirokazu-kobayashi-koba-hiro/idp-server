/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

public class Authentication implements Serializable {
  LocalDateTime time;
  List<String> methods = new ArrayList<>();
  List<String> acrValues = new ArrayList<>();

  public Authentication() {}

  public Authentication setTime(LocalDateTime time) {
    this.time = time;
    return this;
  }

  public Authentication addMethods(List<String> methods) {
    List<String> newValues = new ArrayList<>(this.methods);
    newValues.addAll(methods);
    this.methods = newValues;
    return this;
  }

  public Authentication addAcrValues(List<String> acrValues) {
    List<String> newValues = new ArrayList<>(this.acrValues);
    newValues.addAll(acrValues);
    this.acrValues = newValues;
    return this;
  }

  public LocalDateTime time() {
    return time;
  }

  public boolean hasAuthenticationTime() {
    return Objects.nonNull(time);
  }

  public List<String> methods() {
    return methods;
  }

  public boolean hasMethod() {
    return !methods.isEmpty();
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public String toAcr() {
    if (methods.contains("hwk")) {
      return "urn:mace:incommon:iap:silver";
    }
    return "urn:mace:incommon:iap:bronze";
  }

  public boolean hasAcrValues() {
    return !acrValues.isEmpty();
  }

  public boolean exists() {

    return hasAuthenticationTime();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("time", time);
    map.put("methods", methods);
    map.put("acrValues", acrValues);
    return map;
  }
}
