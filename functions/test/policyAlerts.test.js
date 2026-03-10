const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildPolicyAlertCandidateFromSignal,
  buildPublishedPolicyAlert,
  buildSeedPolicyAlertCandidates,
  extractFederalRegisterSignalsFromResponse,
  extractSignalsFromHtml,
  isLikelyPolicyAlertRelevant,
  isOfficialGovernmentSourceUrl,
  validateCandidateForPublish,
} = require("../lib/policyAlerts");

test("extractFederalRegisterSignalsFromResponse normalizes official results", () => {
  const [signal] = extractFederalRegisterSignalsFromResponse({
    results: [
      {
        title: "Nonimmigrant Academic Students, Exchange Visitors, and Duration of Status",
        abstract: "DHS proposes replacing duration of status with fixed periods of admission.",
        html_url: "https://www.federalregister.gov/documents/2025/08/28/2025-16554/example",
        publication_date: "2025-08-28",
        effective_on: null,
        document_number: "2025-16554",
        type: "PRORULE",
      },
    ],
  });

  assert.equal(signal.sourceLabel, "Federal Register");
  assert.equal(signal.documentNumber, "2025-16554");
  assert.equal(signal.finalityHint, "proposal");
});

test("extractSignalsFromHtml collects matching official anchors", () => {
  const html = `
    <html>
      <body>
        <article>
          <a href="/content/travel/en/News/visas-news/interview-waiver-update-july-25-2025.html">
            Interview Waiver Update
          </a>
          <p>Interview-waiver eligibility narrows for some nonimmigrant visa applicants.</p>
        </article>
      </body>
    </html>
  `;
  const [signal] = extractSignalsFromHtml(
    "https://travel.state.gov/content/travel/en/News/visas-news.html",
    "DOS U.S. Visas News",
    html,
    "/News/visas-news"
  );

  assert.equal(signal.sourceLabel, "DOS U.S. Visas News");
  assert.match(signal.sourceUrl, /travel\.state\.gov/);
  assert.match(signal.title, /Interview Waiver Update/);
});

test("isLikelyPolicyAlertRelevant filters OPT-related content", () => {
  assert.equal(
    isLikelyPolicyAlertRelevant({
      sourceLabel: "USCIS Alerts",
      sourceUrl: "https://www.uscis.gov/alerts/example",
      title: "SEVP updates F-1 travel guidance",
      summary: "OPT students should review updated travel guidance.",
      bodyText: "F-1 students on optional practical training should check travel rules.",
      sourcePublishedAt: null,
      effectiveDate: null,
      effectiveDateText: null,
      documentNumber: null,
      finalityHint: "guidance",
      topicsHint: [],
      audienceHint: null,
      callToActionRoute: null,
      callToActionLabel: null,
    }),
    true
  );
  assert.equal(
    isLikelyPolicyAlertRelevant({
      sourceLabel: "USCIS Alerts",
      sourceUrl: "https://www.uscis.gov/alerts/unrelated",
      title: "Naturalization fee update",
      summary: "This update concerns N-400 filing fees.",
      bodyText: "No student-status content is included here.",
      sourcePublishedAt: null,
      effectiveDate: null,
      effectiveDateText: null,
      documentNumber: null,
      finalityHint: "guidance",
      topicsHint: [],
      audienceHint: null,
      callToActionRoute: null,
      callToActionLabel: null,
    }),
    false
  );
});

test("buildPolicyAlertCandidateFromSignal sets severity, audience, and CTA", () => {
  const candidate = buildPolicyAlertCandidateFromSignal(
    {
      sourceLabel: "DOS U.S. Visas News",
      sourceUrl: "https://travel.state.gov/content/travel/en/News/visas-news/interview-waiver-update-july-25-2025.html",
      title: "Interview waiver update for nonimmigrant visa applicants",
      summary: "Students renewing F-1 visas may need interviews.",
      bodyText: "Interview waiver rules changed for nonimmigrant visa applicants and F-1 students.",
      sourcePublishedAt: Date.UTC(2025, 6, 25),
      effectiveDate: Date.UTC(2025, 8, 2),
      effectiveDateText: null,
      documentNumber: null,
      finalityHint: "operations",
      topicsHint: ["travel"],
      audienceHint: "all_opt_users",
      callToActionRoute: "travelAdvisor",
      callToActionLabel: "Open Travel Advisor",
    },
    Date.UTC(2026, 2, 10)
  );

  assert.equal(candidate.severity, "medium");
  assert.equal(candidate.audience, "all_opt_users");
  assert.equal(candidate.callToActionRoute, "travelAdvisor");
});

test("validateCandidateForPublish requires official source and key text", () => {
  const [candidate] = buildSeedPolicyAlertCandidates(Date.UTC(2026, 2, 10));
  assert.doesNotThrow(() => validateCandidateForPublish(candidate));

  assert.throws(() => validateCandidateForPublish({
    ...candidate,
    source: {
      ...candidate.source,
      url: "https://example.com/not-official",
    },
  }));
});

test("isOfficialGovernmentSourceUrl only accepts https .gov domains", () => {
  assert.equal(isOfficialGovernmentSourceUrl("https://travel.state.gov/content/travel/en/News/visas-news.html"), true);
  assert.equal(isOfficialGovernmentSourceUrl("http://travel.state.gov/content/travel/en/News/visas-news.html"), false);
  assert.equal(isOfficialGovernmentSourceUrl("https://example.com"), false);
});

test("buildPublishedPolicyAlert strips private review fields and stamps lastReviewedAt", () => {
  const [candidate] = buildSeedPolicyAlertCandidates(Date.UTC(2026, 2, 10));
  const published = buildPublishedPolicyAlert(candidate, Date.UTC(2026, 2, 11), "reviewer-1");

  assert.equal(published.candidateId, candidate.id);
  assert.equal(published.lastReviewedAt, Date.UTC(2026, 2, 11));
  assert.equal("status" in published, false);
  assert.equal("rawSourceSnapshot" in published, false);
  assert.equal("publishError" in published, false);
});
