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
