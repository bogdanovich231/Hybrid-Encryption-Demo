const express = require("express");
const router = express.Router();
const keyController = require("../controllers/keyController");

router.get("/get-secret", keyController.encrypted);

module.exports = router;
