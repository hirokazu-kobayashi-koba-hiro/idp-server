import { pki, asn1, util } from "node-forge";
import { digestS256 } from "../hash";
import Base64 from "crypto-js/enc-base64url";
import { getClientCert } from "../../api/cert/clientCert";

export const calculateThumbprint = ({ clientCertFile }) => {
  const clientCert = getClientCert(clientCertFile);
  const cert = pki.certificateFromPem(clientCert);
  const der = asn1.toDer(cert);
  const bytes = new Buffer(util.bytesToHex(der), "hex");
  const s256Hash = digestS256(bytes);
  const thumbprint = Base64.stringify(s256Hash);
  console.log(thumbprint);
  return thumbprint;
};

