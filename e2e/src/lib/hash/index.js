import sha256 from "crypto-js/sha256";
export const digestS256 = (value) => {
  return sha256(value);
};