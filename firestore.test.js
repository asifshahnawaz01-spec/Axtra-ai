const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
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

test("Unauthenticated user cannot read or write user profiles or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
    title: "Test",
    content: "Body",
    createdAt: tsNow(),
    updatedAt: tsNow(),
  }));
});

test("Owner can create and update their profile while ghost fields and cross-user reads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = tsNow();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Asif",
    email: "asif@example.com",
    preferredLanguage: "AUTO",
    createdAt: created,
    updatedAt: created,
  }));

  // Shadow/ghost field injection rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Asif",
    isAdmin: true,
    createdAt: created,
    updatedAt: created,
  }));

  // Cross-user read and write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").get());

  // Listing /users is forbidden
  await assertFails(aliceDb.collection("users").get());
});

test("Owner can CRUD memories, notes, expenses, todos, and routines with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = tsNow();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
    memoryKey: "City",
    memoryValue: "Multan",
    createdAt: created,
    updatedAt: created,
  }));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
    title: "Meeting",
    content: "Call Ali at 5 PM",
    createdAt: created,
    updatedAt: created,
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").set({
    amount: 500,
    currency: "PKR",
    category: "Food",
    description: "Lunch",
    createdAt: created,
    updatedAt: created,
  }));

  // Negative expense rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_bad").set({
    amount: -10,
    currency: "PKR",
    category: "Food",
    description: "Bad",
    createdAt: created,
    updatedAt: created,
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("todos").doc("todo_1").set({
    title: "Buy milk",
    listType: "SHOPPING",
    isDone: false,
    createdAt: created,
    updatedAt: created,
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1").set({
    name: "Morning",
    triggerType: "VOICE",
    triggerValue: "good morning",
    steps: ["Speak daily briefing"],
    isEnabled: true,
    createdAt: created,
    updatedAt: created,
  }));

  // Cross-user access fails on subcollections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("todos").doc("todo_1").delete());

  // Owner can update todo and preserve createdAt
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("todos").doc("todo_1").update({
    isDone: true,
    updatedAt: new Date(Date.now() - 500),
  }));

  // Attempting to mutate createdAt fails
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("todos").doc("todo_1").update({
    createdAt: new Date(Date.now() - 200),
    updatedAt: new Date(Date.now() - 100),
  }));
});
