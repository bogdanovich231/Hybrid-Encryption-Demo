const express = require("express");
const router = express.Router();
const keyController = require("../controllers/keyController");

router.post("/get-secret", keyController.encrypted);

module.exports = router;
