import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { arrayRemove, arrayUnion, deleteField, doc, getDoc, getDocs, collection, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const HID = "house1";
const OWNER = "owner";
const MEMBER = "member";
const OTHER_MEMBER = "member2";
const STRANGER = "stranger";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-necessityq",
    firestore: { rules: readFileSync(fileURLToPath(new URL("../firestore.rules", import.meta.url)), "utf8") },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "households", HID), {
      name: "test",
      joinCode: "ABC123",
      ownerId: OWNER,
      members: [OWNER, MEMBER, OTHER_MEMBER],
    });
    await setDoc(doc(db, "joinCodes", "ABC123"), { householdId: HID });
    await setDoc(doc(db, "households", HID, "lines", "l1"), { name: "milk" });
  });
});

const dbAs = (uid: string) => env.authenticatedContext(uid).firestore();

describe("households: read and data access", () => {
  it("members read the household and its lines", async () => {
    await assertSucceeds(getDoc(doc(dbAs(MEMBER), "households", HID)));
    await assertSucceeds(getDoc(doc(dbAs(MEMBER), "households", HID, "lines", "l1")));
  });

  it("non-members cannot read or write household data", async () => {
    await assertFails(getDoc(doc(dbAs(STRANGER), "households", HID)));
    await assertFails(getDoc(doc(dbAs(STRANGER), "households", HID, "lines", "l1")));
    await assertFails(setDoc(doc(dbAs(STRANGER), "households", HID, "lines", "l2"), { name: "x" }));
  });

  it("unauthenticated users get nothing", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "households", HID)));
  });
});

describe("households: leaving", () => {
  it("a member may remove only themselves", async () => {
    await assertSucceeds(updateDoc(doc(dbAs(MEMBER), "households", HID), { members: arrayRemove(MEMBER) }));
  });

  it("a member may not remove somebody else", async () => {
    await assertFails(updateDoc(doc(dbAs(MEMBER), "households", HID), { members: arrayRemove(OTHER_MEMBER) }));
  });

  it("a leaving member may NOT change ownerId (ownership takeover)", async () => {
    await assertFails(
      updateDoc(doc(dbAs(MEMBER), "households", HID), { members: arrayRemove(MEMBER), ownerId: OTHER_MEMBER }),
    );
    await assertFails(
      updateDoc(doc(dbAs(MEMBER), "households", HID), { members: arrayRemove(MEMBER), ownerId: "nobody" }),
    );
  });

  it("the owner may leave and hand ownership to the next member", async () => {
    await assertSucceeds(
      updateDoc(doc(dbAs(OWNER), "households", HID), { members: arrayRemove(OWNER), ownerId: MEMBER }),
    );
  });
});

describe("households: joining", () => {
  it("a signed-in user may add only themselves", async () => {
    await assertSucceeds(updateDoc(doc(dbAs(STRANGER), "households", HID), { members: arrayUnion(STRANGER) }));
  });

  it("a joiner may not add somebody else or change the owner", async () => {
    await assertFails(updateDoc(doc(dbAs(STRANGER), "households", HID), { members: arrayUnion("friend") }));
    await assertFails(
      updateDoc(doc(dbAs(STRANGER), "households", HID), { members: arrayUnion(STRANGER), ownerId: STRANGER }),
    );
  });

  it("a stranger cannot rename the household", async () => {
    await assertFails(updateDoc(doc(dbAs(STRANGER), "households", HID), { name: "hijacked" }));
  });
});

describe("households: owner powers", () => {
  it("the owner can remove another participant", async () => {
    await assertSucceeds(updateDoc(doc(dbAs(OWNER), "households", HID), { members: arrayRemove(MEMBER) }));
  });

  it("a plain member cannot rename the household", async () => {
    await assertFails(updateDoc(doc(dbAs(MEMBER), "households", HID), { name: "mine now" }));
  });
});

describe("join codes", () => {
  it("a signed-in user may get a code but never list codes", async () => {
    await assertSucceeds(getDoc(doc(dbAs(STRANGER), "joinCodes", "ABC123")));
    await assertFails(getDocs(collection(dbAs(STRANGER), "joinCodes")));
  });

  it("codes are never updated, and only the owner deletes them", async () => {
    await assertFails(updateDoc(doc(dbAs(OWNER), "joinCodes", "ABC123"), { householdId: "other" }));
    await assertFails(setDoc(doc(dbAs(MEMBER), "joinCodes", "ABC123"), { householdId: HID }));
  });
});

describe("messages: reactions and deleting", () => {
  const msg = (db: ReturnType<typeof dbAs>) => doc(db, "households", HID, "messages", "m1");
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(msg(ctx.firestore() as never), { itemId: "milk", authorId: OWNER, text: "hi", at: 1, reactions: { [OTHER_MEMBER]: "😂" } });
    });
  });

  it("a member sets and removes only their own reaction", async () => {
    await assertSucceeds(updateDoc(msg(dbAs(MEMBER)), { [`reactions.${MEMBER}`]: "👍" }));
    await assertSucceeds(updateDoc(msg(dbAs(MEMBER)), { [`reactions.${MEMBER}`]: deleteField() }));
  });

  it("a member may not change somebody else's reaction or the text", async () => {
    await assertFails(updateDoc(msg(dbAs(MEMBER)), { [`reactions.${OTHER_MEMBER}`]: deleteField() }));
    await assertFails(updateDoc(msg(dbAs(MEMBER)), { text: "changed" }));
    await assertFails(updateDoc(msg(dbAs(STRANGER)), { [`reactions.${STRANGER}`]: "👍" }));
  });

  it("only the author marks the message deleted", async () => {
    await assertFails(updateDoc(msg(dbAs(MEMBER)), { deletedAt: 5, text: "" }));
    await assertSucceeds(updateDoc(msg(dbAs(OWNER)), { deletedAt: 5, text: "" }));
  });
});

describe("server-only collections", () => {
  it("config and geocodeCache are closed to clients", async () => {
    await assertFails(getDoc(doc(dbAs(OWNER), "config", "prices")));
    await assertFails(setDoc(doc(dbAs(OWNER), "geocodeCache", "k"), { v: 1 }));
  });

  it("prices are readable by signed-in users but never writable", async () => {
    await assertSucceeds(getDoc(doc(dbAs(STRANGER), "prices", "p1")));
    await assertFails(setDoc(doc(dbAs(OWNER), "prices", "p1"), { v: 1 }));
  });
});
