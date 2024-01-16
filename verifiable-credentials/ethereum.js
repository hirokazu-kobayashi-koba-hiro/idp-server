import Web3 from "web3";

const web3 = new Web3(process.env.WEB3_URL);

export const issueTransaction = async ({ address, privateKey, data }) => {
  try {
    const balance = await getBalance({ address });
    if (balance < 20000000) {
      console.warn("balance is less than 20000000");
      return {
        error: "balance is less than gas price",
      };
    }
    const transaction = await createTransaction({ address, data });
    const signedTransaction = await signTransaction({
      transaction,
      privateKey,
    });
    await sendSignedTransaction({ signedTransaction });
    const transactionResult = await getTransaction({
      transactionHash: signedTransaction.transactionHash,
    });
    return {
      payload: {
        transactionId: signedTransaction.transactionHash,
      },
    };
  } catch (e) {
    console.error(e);
    return {
      error: e,
    };
  }
};

const getBalance = async ({ address }) => {
  const balance = await web3.eth.getBalance(address);
  console.log("getBalance: ", balance);
  return balance;
};

const sendSignedTransaction = async ({ signedTransaction }) => {
  for (let index = 0; index < 10; index++) {
   try {
     const hexRawTransaction = toHex(signedTransaction.rawTransaction);
     console.log("hexRawTransaction: ", hexRawTransaction);
     await web3.eth.sendSignedTransaction(hexRawTransaction);
     console.log("success sendSignedTransaction")
     return
   } catch (e) {
     console.warn(e)
   }
  }
  throw new Error("failed sendSignedTransaction, please retry")
};

const signTransaction = async ({ transaction, privateKey }) => {
  const hexedPrivateKey = toHex(privateKey);
  const signedTransaction = await web3.eth.accounts.signTransaction(
    transaction,
    hexedPrivateKey,
  );
  console.log("signedTransaction:", signedTransaction);
  return signedTransaction;
};

const createTransaction = async ({ address, data }) => {
  const nonce = await getNonceByTransactionCount({ address });
  const toAddress = web3.utils.toChecksumAddress(
    "0xdeaddeaddeaddeaddeaddeaddeaddeaddeaddead",
  );
  const transaction = {
    from: address,
    to: toAddress,
    value: 0,
    data,
    gas: 25000,
    gasPrice: 200000000000,
    chainId: 11155111,
    nonce,
  };
  console.log(transaction);
  return transaction;
};

const getNonceByTransactionCount = async ({ address }) => {
  console.log(address);
  const nonce = await web3.eth.getTransactionCount(address);
  console.info("getTransactionCount nonce: ", nonce);
  return nonce;
};

const getTransaction = async ({ transactionHash }) => {
  const transactionResult = await web3.eth.getTransaction(transactionHash);
  console.log("transactionResult: ", transactionHash);
  return transactionResult;
};
const toHex = (value) => {
  const hexValue = web3.utils.toHex(value);
  console.log(hexValue);
  return hexValue;
};
