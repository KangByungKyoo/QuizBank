import { createHash } from "node:crypto";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export interface GradeCounter { minute: number; minuteCount: number; day: number; dayCount: number; lastAttempt: number }
export function nextGradeCounter(previous: Partial<GradeCounter> | undefined, now: number,
  minuteLimit: number, dayLimit: number, cooldownMs: number): GradeCounter {
  const minute = Math.floor(now / 60_000), day = Math.floor(now / 86_400_000);
  const minuteCount = previous?.minute === minute ? previous.minuteCount ?? 0 : 0;
  const dayCount = previous?.day === day ? previous.dayCount ?? 0 : 0;
  if (minuteCount >= minuteLimit || dayCount >= dayLimit ||
      (previous?.lastAttempt !== undefined && now - previous.lastAttempt < cooldownMs)) {
    throw new HttpsError("resource-exhausted", "QuizBank grading limit reached.");
  }
  return { minute, minuteCount: minuteCount + 1, day, dayCount: dayCount + 1, lastAttempt: now };
}

export async function reserveGradeAttempt(db: Firestore, uid: string): Promise<void> {
  const userRef = db.collection("_quizbankGradeLimits").doc(createHash("sha256").update(uid).digest("hex"));
  const globalRef = db.collection("_quizbankGradeLimits").doc("global");
  await db.runTransaction(async transaction => {
    const [user, global] = await transaction.getAll(userRef, globalRef);
    const now = Date.now();
    const nextUser = nextGradeCounter(user.data(), now, 10, 100, 3000);
    const nextGlobal = nextGradeCounter(global.data(), now, 30, 500, 0);
    // Failed requests also count, and both limits are reserved atomically before calling OpenAI.
    transaction.set(userRef, { ...nextUser, expiresAt: Timestamp.fromMillis(now + 172_800_000) });
    transaction.set(globalRef, nextGlobal);
  });
}
