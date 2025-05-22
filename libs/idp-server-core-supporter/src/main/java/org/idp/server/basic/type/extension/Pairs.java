/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.extension;

public class Pairs<L, R> {
  L left;
  R right;

  public Pairs(L left, R right) {
    this.left = left;
    this.right = right;
  }

  public static <L, R> Pairs<L, R> of(L left, R right) {
    return new Pairs<>(left, right);
  }

  public L getLeft() {
    return this.left;
  }

  public R getRight() {
    return this.right;
  }
}
