const crypto = require("crypto");

exports.register = (req, res) => {
  try {
    const { public_key } = req.body;

    if (!public_key) {
      return res.status(400).json({ error: "public_key is required" });
    }

    global.clientPublicKey = public_key;

    res.json({ status: "ok" });
  } catch (err) {
    res.status(500).json({ error: "Server error", details: err.message });
  }
};

exports.encrypted = (res) => {
  try {
    const aesKey = null;
    if (!global.clientPublicKey) {
      return res.status(400).json({ error: "Public key not registered" });
    }

    if (!aesKey) {
      aesKey = crypto.randomBytes(32);
    }

    const encryptedAES = crypto.publicEncrypt(
      {
        key: clientPublicKey,
        padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
        oaepHash: "sha256",
      },
      aesKeyBuffer
    );

    res.json({
      encrypted_secret: encryptedAES.toString("base64"),
    });
  } catch {
    res.status(500).json({ error: "Server error", details: err.message });
  }
};
