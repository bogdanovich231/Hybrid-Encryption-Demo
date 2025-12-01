const express = require("express");
const router = express.Router();
const keyController = require("../controllers/keyController");

router.post("/register-key", keyController.register);

module.exports = router;
