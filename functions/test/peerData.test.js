const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildGeneralCohortDocIds,
  buildH1bCohortDocIds,
  buildPeerDataBundle,
  deriveCip2DigitFamily,
  deriveOptStartHalfYear,
  sampleSizeBand,
} = require("../lib/peerData");

test("buildPeerDataBundle returns reviewed peer-data metadata", () => {
  const bundle = buildPeerDataBundle(0);

  assert.equal(bundle.version, "2026.03.11");
  assert.ok(bundle.sources.some((source) => source.url.includes("ice.gov")));
  assert.ok(bundle.benchmarkDefinitions.some((definition) => definition.id === "employment_timing"));
  assert.ok(bundle.methodologyNotes.some((note) => /suppression/i.test(note.title)));
});

test("peer cohort helpers derive coarse cohort keys", () => {
  assert.equal(deriveCip2DigitFamily("11.0701"), "11");
  assert.equal(deriveOptStartHalfYear(Date.UTC(2026, 1, 15)), "2026-H1");
  assert.equal(deriveOptStartHalfYear(Date.UTC(2026, 8, 1)), "2026-H2");

  assert.deepEqual(
    buildGeneralCohortDocIds({
      optTrack: "initial_opt",
      cip2digitFamily: "11",
      optStartHalfYear: "2026-H1",
    }),
    [
      "general__initial_opt__11__2026-H1__no_h1b_track",
      "general__initial_opt__11__all_dates__no_h1b_track",
      "general__initial_opt__all_cip__all_dates__no_h1b_track",
    ]
  );

  assert.deepEqual(
    buildH1bCohortDocIds({
      optTrack: "h1b_transition",
      cip2digitFamily: "11",
      optStartHalfYear: "2026-H1",
      h1bTrack: "cap_subject",
    }),
    [
      "h1b__h1b_transition__11__2026-H1__cap_subject",
      "h1b__h1b_transition__11__all_dates__cap_subject",
      "h1b__h1b_transition__all_cip__all_dates__cap_subject",
    ]
  );
});

test("sample size band hides cohorts below suppression threshold", () => {
  assert.equal(sampleSizeBand(29), null);
  assert.equal(sampleSizeBand(30), "30-49");
  assert.equal(sampleSizeBand(75), "50-99");
  assert.equal(sampleSizeBand(100), "100+");
});
