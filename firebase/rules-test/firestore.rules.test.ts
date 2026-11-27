import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  arrayRemove, arrayUnion, deleteDoc, deleteField, doc, getDoc, getDocs, collection, setDoc, updateDoc, writeBatch, serverTimestamp, Timestamp,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const HID = "house1";
const OWNER = "owner";
const MEMBER = "member";
const OTHER_MEMBER = "member2";
const STRANGER = "stranger";
const TOKEN = "Ab3dE5gH7jK9mN2pQ4rS6tUv";
const EXPIRED = "Xx3dE5gH7jK9mN2pQ4rS6tUv";
const inDays = (d: number) => Timestamp.fromMillis(Date.now() + d * 24 * 60 * 60 * 1000);

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
      ownerId: OWNER,
      members: [OWNER, MEMBER, OTHER_MEMBER],
    });
    await setDoc(doc(db, "invites", TOKEN), { householdId: HID, createdBy: OWNER, expiresAt: inDays(3) });
    await setDoc(doc(db, "invites", EXPIRED), { householdId: HID, createdBy: OWNER, expiresAt: inDays(-1) });
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
  // a valid, unused invitation is claimed in the same batch, but the household change is more than adding oneself
  const joinWith = (change: Record<string, unknown>) => {
    const db = dbAs(STRANGER);
    const batch = writeBatch(db);
    batch.update(doc(db, "invites", TOKEN), { usedBy: STRANGER, usedAt: serverTimestamp() });
    batch.update(doc(db, "households", HID), change);
    batch.set(doc(db, "users", STRANGER), { householdId: HID, joinToken: TOKEN }, { merge: true });
    return batch.commit();
  };

  it("a plain signed-in user cannot add themselves without an invitation", async () => {
    await assertFails(updateDoc(doc(dbAs(STRANGER), "households", HID), { members: arrayUnion(STRANGER) }));
  });

  it("a joiner may not add somebody else or change the owner", async () => {
    await assertFails(joinWith({ members: arrayUnion("friend") }));
    await assertFails(joinWith({ members: arrayUnion(STRANGER), ownerId: STRANGER }));
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

describe("one-time invitations", () => {
  const join = (uid: string, token: string, hid = HID) => {
    const db = dbAs(uid);
    const batch = writeBatch(db);
    batch.update(doc(db, "invites", token), { usedBy: uid, usedAt: serverTimestamp() });
    batch.update(doc(db, "households", hid), { members: arrayUnion(uid) });
    batch.set(doc(db, "users", uid), { householdId: hid, joinToken: token }, { merge: true });
    return batch.commit();
  };

  it("a signed-in user may get an invitation by its token but never list them", async () => {
    await assertSucceeds(getDoc(doc(dbAs(STRANGER), "invites", TOKEN)));
    await assertFails(getDocs(collection(dbAs(STRANGER), "invites")));
  });

  it("a member creates an invitation for their own household, valid at most a week", async () => {
    const mk = (uid: string, data: Record<string, unknown>) => setDoc(doc(dbAs(uid), "invites", "NewToken123456789012345"), data);
    const ok = { householdId: HID, createdBy: MEMBER, createdAt: serverTimestamp(), expiresAt: inDays(7) };
    await assertSucceeds(mk(MEMBER, ok));
    await assertFails(mk(STRANGER, { ...ok, createdBy: STRANGER }));
    await assertFails(mk(MEMBER, { ...ok, expiresAt: inDays(30) }));
    await assertFails(mk(MEMBER, { ...ok, createdBy: OWNER }));
  });

  it("a stranger joins with a valid invitation, and it is used up", async () => {
    await assertSucceeds(join(STRANGER, TOKEN));
    await assertSucceeds(getDoc(doc(dbAs(STRANGER), "households", HID)));
  });

  it("the same invitation cannot be used twice", async () => {
    await assertSucceeds(join(STRANGER, TOKEN));
    await assertFails(join("second-stranger", TOKEN));
  });

  it("an expired invitation does not work", async () => {
    await assertFails(join(STRANGER, EXPIRED));
  });

  it("adding yourself without an invitation is refused", async () => {
    await assertFails(updateDoc(doc(dbAs(STRANGER), "households", HID), { members: arrayUnion(STRANGER) }));
  });

  it("an invitation to one household cannot open another", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "households", "house2"), { name: "other", ownerId: "someone", members: ["someone"] });
    });
    await assertFails(join(STRANGER, TOKEN, "house2"));
  });

  it("the old short join codes no longer exist as a way in", async () => {
    await assertFails(getDoc(doc(dbAs(STRANGER), "joinCodes", "ABC123")));
  });

  it("an invitation is never edited otherwise, and only its creator or the owner revokes it", async () => {
    await assertFails(updateDoc(doc(dbAs(OWNER), "invites", TOKEN), { householdId: "other" }));
    await assertFails(setDoc(doc(dbAs(STRANGER), "invites", TOKEN), { householdId: HID }));
    await assertFails(deleteDoc(doc(dbAs(MEMBER), "invites", TOKEN)));
    await assertSucceeds(deleteDoc(doc(dbAs(OWNER), "invites", TOKEN)));
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
