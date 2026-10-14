import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { gradeAnswer, validateGradeRequest } from "./grading";
import { reserveGradeAttempt } from "./rateLimit";

initializeApp();
const openaiApiKey = defineSecret("OPENAI_API_KEY");
const quizbankAppId = defineString("QUIZBANK_ANDROID_APP_ID");
const gradingModel = defineString("QUIZBANK_GRADING_MODEL", { default: "gpt-4.1-mini" });

export const gradeQuizAnswer = onCall({
  region: "asia-northeast3", secrets: [openaiApiKey],
  enforceAppCheck: true, consumeAppCheckToken: true,
  timeoutSeconds: 30, memory: "256MiB", minInstances: 0, maxInstances: 2, concurrency: 10
}, async request => {
  request.rawRequest.res?.set("Cache-Control", "no-store");
  const { uid, input } = validateGradeRequest(request.auth, request.app, quizbankAppId.value(), request.data);
  try {
    await reserveGradeAttempt(getFirestore(), uid);
    return await gradeAnswer(openaiApiKey.value(), gradingModel.value(), uid, input);
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    // Never log or return credentials, question contents, answers, or raw upstream errors.
    throw new HttpsError("unavailable", "QuizBank grading unavailable.");
  }
});
