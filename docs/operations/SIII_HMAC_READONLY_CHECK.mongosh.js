// Run in an already authenticated mongosh session for Cluster0.
// Read only: no credentials, ciphertext, connection strings or academic data are printed.
// This does not verify decryption or authorize provisioning.
(() => {
  const database = db.getSiblingDB("companero_dev");
  const names = ["institutional_integration_credentials", "platform_institutions"];
  const listed = database.runCommand({
    listCollections: 1,
    filter: { name: { $in: names } },
    nameOnly: true,
    cursor: { batchSize: 10 },
  });
  if (listed.ok !== 1) throw new Error("Collection inspection failed; do not bootstrap");
  const present = new Set(listed.cursor.firstBatch.map(item => item.name));
  const credentials = present.has(names[0]) ? database.getCollection(names[0]) : null;
  const institutions = present.has(names[1]) ? database.getCollection(names[1]) : null;
  printjson({
    database: database.getName(),
    observedAt: new Date(),
    credentialsCollectionExists: credentials !== null,
    credentialCount: credentials ? credentials.countDocuments({}) : 0,
    credentialMetadata: credentials ? credentials.find({}, {
      _id: 1,
      tenantId: 1,
      scopes: 1,
      enabled: 1,
      version: 1,
      expiresAtEpochSeconds: 1,
      revokedAt: 1,
    }).limit(100).toArray() : [],
    institutionsCollectionExists: institutions !== null,
    institutionCount: institutions ? institutions.countDocuments({}) : 0,
    institutionMetadata: institutions ? institutions.find({}, {
      _id: 1,
      displayName: 1,
      registrationEnabled: 1,
    }).limit(100).toArray() : [],
    metadataLimit: 100,
    decryptionVerified: false,
    writesPerformed: false,
  });
})();
