const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildDeterministicRecommendedAction,
  normalizeCaseStage,
  normalizeFormType,
} = require("../lib/uscisCaseTracker");

test("normalizeFormType supports I-129", () => {
  assert.equal(normalizeFormType("I-129"), "I-129");
  assert.equal(normalizeFormType("i129 petition"), "I-129");
});

test("normalizeCaseStage maps I-129 approval states", () => {
  const stage = normalizeCaseStage(
    "I-129",
    "Case Was Approved",
    "We approved your Form I-129."
  );
  assert.equal(stage, "APPROVED");
});

test("buildDeterministicRecommendedAction returns I-129-specific guidance", () => {
  const action = buildDeterministicRecommendedAction("I-129", "APPROVED");
  assert.match(action, /approval notice/i);
  assert.match(action, /effective date/i);
});
