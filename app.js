const express = require("express");
const app = express();

app.use(express.json());

global.clientPublicKey = null;

app.use("/api", require("./routes/registerKey"));

app.listen(3000, () => console.log("Server running on port 3000"));
