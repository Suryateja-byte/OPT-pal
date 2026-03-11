const test = require("node:test");
const assert = require("node:assert/strict");

const {buildVisaPathwayPlannerBundle} = require("../lib/visaPathwayPlanner");

test("buildVisaPathwayPlannerBundle returns reviewed official sources", () => {
  const bundle = buildVisaPathwayPlannerBundle();

  assert.equal(bundle.version, "2026.03.11");
  assert.equal(bundle.staleAfterDays, 30);
  assert.ok(bundle.sources.some((source) => source.url.includes("uscis.gov")));
  assert.ok(bundle.sources.some((source) => source.url.includes("travel.state.gov")));
});

test("buildVisaPathwayPlannerBundle includes published FY 2027 H-1B dates", () => {
  const bundle = buildVisaPathwayPlannerBundle();

  assert.equal(bundle.h1bSeason.fiscalYear, 2027);
  assert.equal(bundle.h1bSeason.isPublished, true);
  assert.ok(bundle.h1bSeason.registrationOpenDate > 0);
  assert.ok(bundle.h1bSeason.registrationCloseDate > 0);
  assert.ok(bundle.h1bSeason.petitionFilingEarliestDate > 0);
  assert.match(bundle.h1bSeason.notes, /March 4, 2026/i);
});
