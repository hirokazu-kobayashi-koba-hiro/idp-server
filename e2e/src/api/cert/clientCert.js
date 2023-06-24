import * as fs from "fs";

export const getClientCert = (clientCertFile) => {
  const clientCert = fs.readFileSync(__dirname + "/" +clientCertFile).toString();
  console.log(clientCert);
  return clientCert;
};
export const encodedClientCert = (clientCertFile) => {
  const clientCert = getClientCert(clientCertFile);
  return clientCert.replaceAll("\n", "%0A");
};