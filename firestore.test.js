const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_susi";
const ADMIN_EMAIL = "susimelani2108@gmail.com";
const USER_A_UID = "user_alpha_001";
const USER_B_UID = "user_beta_002";
const DEVICE_A = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
const DEVICE_B = "ff00ee11dd22cc33bb44aa5599668877";
const KEY_CODE = "NOVA-7X9K-2M4P";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function spoofedAdminDb() {
  return testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function userDb(uid) {
  return testEnv
    .authenticatedContext(uid, {
      email: `${uid}@example.com`,
      email_verified: true,
    })
    .firestore();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("licenseKeys").doc(KEY_CODE).get());
  await assertFails(unauthDb.collection("licenseKeys").get());
  await assertFails(unauthDb.collection("adminKeyNotes").get());
  await assertFails(unauthDb.collection("adminConfig").doc("security").get());
  await assertFails(
    unauthDb.collection("licenseKeys").doc(KEY_CODE).set({
      durationDays: 7,
      status: "active",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing (unverified admin email) is strictly denied", async () => {
  const spoof = spoofedAdminDb();
  const now = new Date(Date.now() - 1000);
  await assertFails(spoof.collection("licenseKeys").get());
  await assertFails(
    spoof.collection("licenseKeys").doc(KEY_CODE).set({
      durationDays: 7,
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(spoof.collection("adminKeyNotes").get());
  await assertFails(spoof.collection("adminConfig").doc("security").get());
});

test("Admin can create, list, update duration, reset device, revoke, manage buyer note & PIN, and delete", async () => {
  const db = adminDb();
  const created = new Date(Date.now() - 5000);

  // Create key (mirrors Kotlin LicenseRepository.createLicenseKey)
  await assertSucceeds(
    db.collection("licenseKeys").doc(KEY_CODE).set({
      durationDays: 7,
      status: "active",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field on create (Shadow Update / Create test)
  await assertFails(
    db.collection("licenseKeys").doc("NOVA-GHOST-1").set({
      durationDays: 7,
      status: "active",
      isVip: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create isolated buyer note (mirrors Kotlin updateBuyerNote/createLicenseKey)
  await assertSucceeds(
    db.collection("adminKeyNotes").doc(KEY_CODE).set({
      buyerNote: "Budi Santoso - Paket 7 Hari",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Admin can list both collections (mirrors observeAllAdminKeys)
  await assertSucceeds(db.collection("licenseKeys").get());
  await assertSucceeds(db.collection("adminKeyNotes").get());

  // Admin can create and update salted PIN config
  await assertSucceeds(
    db.collection("adminConfig").doc("security").set({
      pinSalt: "0123456789abcdef0123456789abcdef",
      pinHash: "a".repeat(64),
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    db.collection("adminConfig").doc("security").update({
      pinSalt: "fedcba9876543210fedcba9876543210",
      pinHash: "b".repeat(64),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin can update durationDays and status
  await assertSucceeds(
    db.collection("licenseKeys").doc(KEY_CODE).update({
      durationDays: 30,
      status: "revoked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin cannot tamper with immutable createdAt
  await assertFails(
    db.collection("licenseKeys").doc(KEY_CODE).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can delete key and note
  await assertSucceeds(db.collection("licenseKeys").doc(KEY_CODE).delete());
  await assertSucceeds(db.collection("adminKeyNotes").doc(KEY_CODE).delete());
});

test("Regular user can claim an unused active key once, cannot tamper expiry, and 2nd device is rejected", async () => {
  const created = new Date(Date.now() - 10000);
  await adminDb().collection("licenseKeys").doc(KEY_CODE).set({
    durationDays: 7,
    status: "active",
    createdAt: created,
    updatedAt: created,
  });
  await adminDb().collection("adminKeyNotes").doc(KEY_CODE).set({
    buyerNote: "Confidential Buyer PII",
    createdAt: created,
    updatedAt: created,
  });

  const userA = userDb(USER_A_UID);
  const userB = userDb(USER_B_UID);

  // Regular users CANNOT list keys, read buyer notes, or read PIN config
  await assertFails(userA.collection("licenseKeys").get());
  await assertFails(userA.collection("adminKeyNotes").doc(KEY_CODE).get());
  await assertFails(userA.collection("adminKeyNotes").get());
  await assertFails(userA.collection("adminConfig").doc("security").get());

  // Regular user CAN fetch specific key by exact ID
  await assertSucceeds(userA.collection("licenseKeys").doc(KEY_CODE).get());

  // Tampered expiry (+30 days on a 7-day key) MUST fail at rules layer
  const now = Date.now();
  await assertFails(
    userA.collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_A,
      boundDeviceModel: "Samsung S24",
      boundUserId: USER_A_UID,
      activatedAt: new Date(now - 1000),
      expiresAt: new Date(now + 30 * 86400 * 1000),
      updatedAt: new Date(now - 1000),
    })
  );

  // Valid first-time activation on Device A succeeds (mirrors Kotlin Transaction Action A)
  const validExp = new Date(now + 7 * 86400 * 1000);
  await assertSucceeds(
    userA.collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_A,
      boundDeviceModel: "Samsung S24",
      boundUserId: USER_A_UID,
      activatedAt: new Date(now - 1000),
      expiresAt: validExp,
      updatedAt: new Date(now - 1000),
    })
  );

  // Device B (User B) attempting to overwrite boundDeviceId MUST fail!
  await assertFails(
    userB.collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_B,
      boundDeviceModel: "Pixel 9",
      boundUserId: USER_B_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin resets device binding (preserving activatedAt & expiresAt)
  await assertSucceeds(
    adminDb().collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: null,
      boundDeviceModel: null,
      boundUserId: null,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Device B attempting to extend expiresAt during re-bind MUST fail
  await assertFails(
    userB.collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_B,
      boundDeviceModel: "Pixel 9",
      boundUserId: USER_B_UID,
      expiresAt: new Date(Date.now() + 90 * 86400 * 1000),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Device B re-binding cleanly (preserving expiresAt) succeeds (mirrors Kotlin Transaction Action B)
  await assertSucceeds(
    userB.collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_B,
      boundDeviceModel: "Pixel 9",
      boundUserId: USER_B_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Revoked key cannot be claimed by regular user", async () => {
  const created = new Date(Date.now() - 10000);
  await adminDb().collection("licenseKeys").doc(KEY_CODE).set({
    durationDays: 7,
    status: "revoked",
    createdAt: created,
    updatedAt: created,
  });

  const now = Date.now();
  await assertFails(
    userDb(USER_A_UID).collection("licenseKeys").doc(KEY_CODE).update({
      boundDeviceId: DEVICE_A,
      boundDeviceModel: "Pixel 8",
      boundUserId: USER_A_UID,
      activatedAt: new Date(now - 1000),
      expiresAt: new Date(now + 7 * 86400 * 1000),
      updatedAt: new Date(now - 1000),
    })
  );
});
