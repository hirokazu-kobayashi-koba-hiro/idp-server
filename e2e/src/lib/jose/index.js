// eslint-disable-next-line no-undef
const jwt = require("jsonwebtoken");
import jwkToPem from "jwk-to-pem";

const createJwt = ({ payload, secret, options }) => {
  return jwt.sign(payload, secret, options);
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
