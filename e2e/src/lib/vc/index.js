const { Certificate } = require('@blockcerts/cert-verifier-js/dist/verifier-node/index.js');
const { Decoder } = require('@vaultie/lds-merkle-proof-2019');

export const verifyBlockCert = async (certificateDefinition) => {
  const certificate = new Certificate(certificateDefinition);
  await certificate.init();
  const verificationResult = await certificate.verify(({code, label, status, errorMessage}) => {
    console.log("Code:", code, label, " - Status:", status);
    if (errorMessage) {
      console.log(`The step ${code} fails with the error: ${errorMessage}`);
    }
  });

  if (verificationResult.status === "failure") {
    console.log(`The certificate is not valid. Error: ${verificationResult.errorMessage}`);
  }
  return verificationResult;
};
export const decodeWithBase58 = (value) => {
  const decoder = new Decoder(value)
  const decodedValue = decoder.decode();
  console.log(decodedValue);
  return decodedValue;
}