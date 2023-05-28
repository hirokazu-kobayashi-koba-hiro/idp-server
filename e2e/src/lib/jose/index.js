// eslint-disable-next-line no-undef
const jwt = require("jsonwebtoken");
const jose = require("jose")
import jwkToPem from "jwk-to-pem";
import jwt_decode from "jwt-decode";
import { v4 as uuidv4 } from "uuid";
export const generateJti = () => {
  return uuidv4();
};
export const createJwt = ({ payload, secret, options }) => {
  return jwt.sign(payload, secret, options);
};

export const createJwe = async ({ text, key, enc }) => {
  const keyObject = await jose.importJWK(key);
  console.log(keyObject);
  const jwe = await new jose.CompactEncrypt(new TextEncoder().encode(text))
    .setProtectedHeader({
      kid: key.kid,
      alg: key.alg,
      enc,
      cty: "JWT",
    })
    .encrypt(keyObject);
  console.log(jwe);
  return jwe;
};

export const createJwtWithPrivateKey = ({
  payload,
  privateKey,
  additionalOptions,
}) => {
  const secret = jwkToPem(privateKey, { private: true });
  const options = {
    ...additionalOptions,
    keyid: privateKey.kid,
    algorithm: privateKey.alg,
  };
  return createJwt({
    payload,
    secret,
    options,
  });
};

export const verifyAndDecodeIdToken = ({ idToken, jwks }) => {
  const header = jwt_decode(idToken, { header: true });
  const payload = jwt_decode(idToken);
  const jwk = jwks.keys.filter((jwk) => jwk.kid === header.kid)[0];
  const publicKey = jwkToPem(jwk);
  const verifyResult = verifySignature({ idToken, publicKey });
  return {
    header,
    payload,
    verifyResult,
  };
};

export const verifySignature = ({ idToken, publicKey }) => {
  try {
    jwt.verify(idToken, publicKey);
    return true;
  } catch (e) {
    console.log(e);
    return false;
  }
};
