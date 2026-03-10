const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildDeterministicRecommendedAction,
  computeStatusHash,
  normalizeI765Stage,
  normalizeReceiptNumber,
  parseUscisCasePayload,
  sanitizeHtmlText,
} = require("../lib/uscisCaseTracker");

test("normalizeReceiptNumber enforces USCIS receipt format", () => {
  assert.equal(normalizeReceiptNumber("msc1234567890"), "MSC1234567890");
  assert.throws(() => normalizeReceiptNumber("bad"));
});

test("sanitizeHtmlText strips tags and entities", () => {
  assert.equal(
    sanitizeHtmlText("<p>Case Was <strong>Approved</strong>&nbsp;</p>"),
    "Case Was Approved"
  );
});

test("normalizeI765Stage maps card production and risky statuses", () => {
  assert.equal(
    normalizeI765Stage("Card Is Being Produced", "USCIS is producing your card."),
    "CARD_PRODUCED"
  );
  assert.equal(
    normalizeI765Stage("Request for Evidence Was Sent", "Please respond."),
    "RFE_OR_NOID"
  );
});

test("parseUscisCasePayload supports USCIS-style snake_case fields", () => {
  const parsed = parseUscisCasePayload(
    {
      receipt_num: "SRC1234567890",
      form_num: "I-765",
      current_case_status_text: "Case Was Received",
      current_case_status_desc: "<p>We received your case.</p>",
      modified_dt: "2026-03-10T12:00:00Z",
      hist_case_status: [
        {
          status_text: "Case Was Received",
          status_desc: "<p>We received your case.</p>",
          status_date: "2026-03-09T12:00:00Z",
        },
      ],
    },
    "SRC1234567890"
  );

  assert.equal(parsed.receiptNumber, "SRC1234567890");
  assert.equal(parsed.formType, "I-765");
  assert.equal(parsed.officialStatusDescription, "We received your case.");
  assert.equal(parsed.history.length, 1);
});

test("computeStatusHash changes when official text changes", () => {
  const hashA = computeStatusHash("Case Was Received", "One", 1000);
  const hashB = computeStatusHash("Case Was Approved", "One", 1000);
  assert.notEqual(hashA, hashB);
});

test("deterministic action escalates denial flows", () => {
  assert.match(
    buildDeterministicRecommendedAction("DENIED"),
    /Contact your DSO or an immigration attorney/
  );
});
