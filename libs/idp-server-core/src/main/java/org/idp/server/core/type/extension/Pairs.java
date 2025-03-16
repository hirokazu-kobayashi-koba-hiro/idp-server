package org.idp.server.core.type.extension;

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
