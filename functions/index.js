/**
 * Import function triggers from their respective submodules.
 *
 * See: https://firebase.google.com/docs/functions/organize-functions
 */

const { identifyPlantV2 } = require("./v2/identify");

// Export the function for deployment
exports.identifyPlantV2 = identifyPlantV2;
