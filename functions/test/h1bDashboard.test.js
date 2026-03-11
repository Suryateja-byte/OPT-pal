const test = require("node:test");
const assert = require("node:assert/strict");

const {buildH1bDashboardBundle} = require("../lib/h1bDashboard");

test("buildH1bDashboardBundle returns current policy bundle", () => {
  const bundle = buildH1bDashboardBundle();

  assert.equal(bundle.version, "2026.03.11");
  assert.equal(bundle.capSeason.fiscalYear, 2027);
  assert.ok(bundle.capSeason.registrationOpenAt > 0);
  assert.ok(bundle.capSeason.registrationCloseAt > 0);
  assert.ok(bundle.capSeason.petitionFilingOpensAt > 0);
  assert.ok(bundle.citations.some((citation) => citation.url.includes("uscis.gov")));
  assert.ok(bundle.ruleCards.some((card) => /No lottery/i.test(card.title)));
});
