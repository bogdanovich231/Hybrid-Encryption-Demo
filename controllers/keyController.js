const crypto = require("crypto");

let clientPublicKey = null;
let aesKey = null;

exports.register = (req, res) => {
  try {
    const { public_key } = req.body;

    if (!public_key) {
      return res.status(400).json({ error: "public_key is required" });
    }

    clientPublicKey = public_key;
    console.log("Public key registered successfully");
    console.log("Public key (first 100 chars):", public_key.substring(0, 100));

    res.json({ status: "ok" });
  } catch (err) {
    console.error("Error in register:", err);
    res.status(500).json({ error: "Server error", details: err.message });
  }
};

exports.encrypted = (req, res) => {
  try {
    if (!clientPublicKey) {
      return res.status(400).json({
        error: "Public key not registered. Call /register-key first.",
      });
    }


    aesKey = crypto.randomBytes(32);
    console.log("AES key generated:", aesKey.length * 8, "bits");

    const publicKeyPem = `-----BEGIN PUBLIC KEY-----\n${clientPublicKey}\n-----END PUBLIC KEY-----`;

    const encryptedAES = crypto.publicEncrypt(
      {
        key: publicKeyPem,
        padding: crypto.constants.RSA_PKCS1_PADDING, 
      },
      aesKey
    );

    console.log("AES key encrypted with PKCS1 padding");

    res.json({
      encrypted_secret: encryptedAES.toString("base64"),
    });
  } catch (err) {
    console.error("Error in encrypted:", err);
    res.status(500).json({ error: "Server error", details: err.message });
  }
};

exports.message = (req, res) => {
  try {
    if (!aesKey) {
      return res
        .status(400)
        .json({ error: "AES key not established. Call /get-secret first." });
    }

    const timeStamp = new Date().toISOString();
    const plaintext = `Pozdrowienia z serwera, czas: ${timeStamp}`;
    console.log("Plaintext message:", plaintext);

    const iv = crypto.randomBytes(12); 
    console.log("IV (hex):", iv.toString("hex"));

    const cipher = crypto.createCipheriv("aes-256-gcm", aesKey, iv);
    const encrypted = Buffer.concat([
      cipher.update(plaintext, "utf8"),
      cipher.final(),
    ]);

    const tag = cipher.getAuthTag();
    console.log("Auth tag (hex):", tag.toString("hex"));

    const payload = Buffer.concat([iv, encrypted, tag]);
    console.log("Total payload length:", payload.length, "bytes");

    res.json({ ciphertext: payload.toString("base64") });
  } catch (err) {
    console.error("Error in message:", err);
    res.status(500).json({ error: "Server error", details: err.message });
  }
};
