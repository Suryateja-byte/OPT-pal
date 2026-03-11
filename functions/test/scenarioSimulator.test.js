const test = require("node:test");
const assert = require("node:assert/strict");

const {buildScenarioSimulatorBundle} = require("../lib/scenarioSimulator");

test("buildScenarioSimulatorBundle returns reviewed scenario metadata", () => {
  const response = buildScenarioSimulatorBundle(0);

  assert.equal(response.version, "2026.03.11");
  assert.ok(response.sources.some((source) => source.url.includes("uscis.gov")));
  assert.ok(response.scenarioDefinitions.some((definition) => definition.templateId === "international_travel"));
  assert.ok(response.ruleCards.some((card) => /180-day/i.test(card.summary)));
});
