import { HttpsError, onCall } from "firebase-functions/v2/https";

const SCENARIO_SOURCES = [
  {
    id: "uscis_policy_manual_opt",
    label: "USCIS Policy Manual, Volume 2 Part F Chapter 5",
    url: "https://www.uscis.gov/policy-manual/volume-2-part-f-chapter-5",
    effectiveDate: "Current as published",
    lastReviewedDate: "2026-03-11",
    summary: "Primary source for OPT and STEM OPT employment, reporting, and unemployment rules.",
  },
  {
    id: "study_in_states_reporting",
    label: "Study in the States STEM Reporting Requirements",
    url: "https://studyinthestates.dhs.gov/stem-opt-hub/reporting-requirements",
    effectiveDate: "Current as published",
    lastReviewedDate: "2026-03-11",
    summary: "STEM validation, evaluation, and reporting timing requirements.",
  },
  {
    id: "uscis_stem_extension",
    label: "USCIS STEM OPT Extension",
    url: "https://www.uscis.gov/working-in-the-united-states/students-and-exchange-visitors/optional-practical-training-extension-for-stem-students-stem-opt",
    effectiveDate: "Current as published",
    lastReviewedDate: "2026-03-11",
    summary: "STEM filing and 180-day automatic extension guidance.",
  },
  {
    id: "uscis_cap_gap",
    label: "USCIS Cap-Gap Guidance",
    url: "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/extension-of-post-completion-optional-practical-training-opt-and-f-1-status-for-eligible-students",
    effectiveDate: "Current as published",
    lastReviewedDate: "2026-03-11",
    summary: "Cap-gap continuity, April 1 timing, and hard-stop travel constraints.",
  },
];

export function buildScenarioSimulatorBundle(now: number = Date.now()) {
  return {
    version: "2026.03.11",
    generatedAt: now,
    lastReviewedAt: Date.UTC(2026, 2, 11),
    staleAfterDays: 30,
    sources: SCENARIO_SOURCES,
    scenarioDefinitions: [
      definition("job_loss_or_interruption", "Job loss or interruption", "Forecast unemployment exposure, continuity, and escalation timing.", "Set the interruption date first, then add a replacement employer only if you have a realistic start date.", "This does not predict reinstatement or excuse status violations."),
      definition("add_or_switch_employer", "Add or switch employer", "Check STEM employer-change readiness, E-Verify dependence, and reporting drag.", "Use this when a new employer may require a new I-983, new I-20, or both.", "This does not confirm a job is degree-related or automatically SEVIS-compliant."),
      definition("reporting_deadline_missed", "Reporting deadline missed", "Stress-test overdue reporting and the next clean-up step.", "Use the actual due date if you know it. Missed STEM validations should stay conservative.", "This does not erase the underlying missed deadline."),
      definition("international_travel", "International travel", "Run the travel rules engine against a hypothetical trip without changing real travel data.", "Model the trip dates and posture you are seriously considering, especially cap-gap and visa-renewal facts.", "This does not guarantee admission or visa issuance."),
      definition("h1b_cap_continuity", "H-1B cap continuity", "Project cap-gap continuity, petition stage, and travel sensitivity.", "Keep this aligned with the petition stage your employer can actually document.", "This does not estimate lottery odds or petition approval."),
      definition("pending_stem_extension", "Pending STEM extension", "Model timely filing, the 180-day bridge, and STEM pending-case edge cases.", "Use the real filing date if available. Timely-filed STEM cases use the 180-day extension, not 540 days.", "This does not replace DSO review for employer changes while pending."),
    ],
    ruleCards: [
      {
        id: "no_predictions",
        title: "No predictive outputs",
        summary: "Scenario Simulator stays deterministic. It does not produce lottery odds, approval forecasts, or individualized legal predictions.",
        confidence: "Verified",
        whatThisDoesNotMean: "A green result is not a legal opinion or guarantee.",
        citationIds: ["uscis_policy_manual_opt", "uscis_cap_gap"],
      },
      {
        id: "stem_180_day_bridge",
        title: "Pending STEM bridge stays at 180 days",
        summary: "Timely-filed STEM OPT extensions keep the F-1-specific 180-day automatic extension rule.",
        confidence: "Verified",
        whatThisDoesNotMean: "The general 540-day EAD extension rule does not replace the STEM-specific rule here.",
        citationIds: ["uscis_stem_extension"],
      },
      {
        id: "cap_gap_travel",
        title: "Cap-gap travel remains a hard-stop branch",
        summary: "Travel during a sensitive pending H-1B change-of-status scenario escalates directly to consult DSO or attorney.",
        confidence: "Verified",
        whatThisDoesNotMean: "A travel itinerary does not pause or preserve cap-gap automatically.",
        citationIds: ["uscis_cap_gap"],
      },
    ],
    changelog: [
      {
        id: "initial_release",
        title: "Initial scenario simulator policy bundle",
        summary: "First reviewed bundle covering OPT, STEM, travel, and H-1B continuity templates.",
        effectiveDate: "2026-03-11",
        citationId: "uscis_policy_manual_opt",
      },
    ],
  };
}

export const getScenarioSimulatorBundle = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  return buildScenarioSimulatorBundle();
});

function definition(
  templateId: string,
  title: string,
  summary: string,
  editorHint: string,
  resultCaveat: string
) {
  return {
    templateId,
    title,
    summary,
    editorHint,
    resultCaveat,
  };
}
