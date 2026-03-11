import { HttpsError, onCall } from "firebase-functions/v2/https";

export const getVisaPathwayPlannerBundle = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  return buildVisaPathwayPlannerBundle();
});

export function buildVisaPathwayPlannerBundle() {
  const lastReviewedAt = Date.UTC(2026, 2, 11);
  return {
    version: "2026.03.11",
    generatedAt: Date.now(),
    lastReviewedAt,
    staleAfterDays: 30,
    sources: [
      source(
        "uscis_stem_options",
        "USCIS STEM employment options",
        "https://www.uscis.gov/working-in-the-united-states/options-for-alien-stem-professionals-to-work-in-the-united-states",
        "2025-12-12",
        "Temporary and permanent immigration options for STEM professionals."
      ),
      source(
        "uscis_nonimmigrant_stem",
        "USCIS nonimmigrant STEM pathways",
        "https://www.uscis.gov/working-in-the-united-states/stem-employment-pathways/nonimmigrant-pathways-for-stem-employment-in-the-united-states",
        "2025-12-12",
        "Official nonimmigrant employment pathways for STEM workers."
      ),
      source(
        "uscis_immigrant_stem",
        "USCIS immigrant STEM pathways",
        "https://www.uscis.gov/working-in-the-united-states/stem-employment-pathways/immigrant-pathways-for-stem-employment-in-the-united-states",
        "2025-12-12",
        "Official immigrant employment pathways for STEM workers."
      ),
      source(
        "uscis_stem_opt",
        "USCIS STEM OPT",
        "https://www.uscis.gov/working-in-the-united-states/students-and-exchange-visitors/optional-practical-training-extension-for-stem-students-stem-opt",
        "2025-12-12",
        "Core STEM OPT filing requirements and reminders."
      ),
      source(
        "study_stem_cip",
        "Study in the States STEM CIP guidance",
        "https://studyinthestates.dhs.gov/stem-opt-hub/eligible-cip-codes-for-the-stem-opt-extension",
        "2025-12-12",
        "Official STEM-designated CIP guidance for extension planning."
      ),
      source(
        "uscis_h1b_specialty",
        "USCIS H-1B specialty occupations",
        "https://www.uscis.gov/working-in-the-united-states/h-1b-specialty-occupations?stream=top",
        "2025-12-12",
        "Specialty occupation overview and filing basics."
      ),
      source(
        "uscis_h1b_registration",
        "USCIS H-1B electronic registration",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/h-1b-electronic-registration-process",
        "2026-03-04",
        "Electronic registration process and published cap-season dates."
      ),
      source(
        "uscis_cap_gap",
        "USCIS cap-gap extension",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/extension-of-post-completion-optional-practical-training-opt-and-f-1-status-for-eligible-students",
        "2025-01-17",
        "Cap-gap background for continuity planning."
      ),
      source(
        "uscis_o1",
        "USCIS O-1",
        "https://www.uscis.gov/working-in-the-united-states/temporary-workers/o-1-visa-individuals-with-extraordinary-ability-or-achievement",
        "2025-12-12",
        "Official O-1 extraordinary ability criteria and filing structure."
      ),
      source(
        "uscis_eb1",
        "USCIS EB-1",
        "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-first-preference-eb-1",
        "2025-12-12",
        "EB-1 categories and filing structure."
      ),
      source(
        "uscis_eb2",
        "USCIS EB-2",
        "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-second-preference-eb-2",
        "2025-12-12",
        "EB-2 including NIW overview."
      ),
      source(
        "uscis_eb3",
        "USCIS EB-3",
        "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-third-preference-eb-3",
        "2025-12-12",
        "EB-3 overview for employer-sponsored green-card tracks."
      ),
      source(
        "dol_perm",
        "DOL FLAG PERM",
        "https://flag.dol.gov/programs/perm",
        "2025-12-12",
        "Official PERM labor-certification entry point."
      ),
      source(
        "dos_visa_bulletin_march_2026",
        "DOS Visa Bulletin March 2026",
        "https://travel.state.gov/content/travel/en/legal/visa-law0/visa-bulletin/2026/visa-bulletin-for-march-2026.html",
        "2026-03-01",
        "Current March 2026 visa bulletin link for long-term educational tracks."
      ),
    ],
    pathwayDefinitions: [
      definition("stem_opt", "STEM OPT", "Best near-term path when your degree, employer, and I-983 workup line up.", ["uscis_stem_opt", "study_stem_cip"], [
        step("open_i983", "Build or update Form I-983", "Use the assistant to prepare the training plan.", "i983Assistant", null),
        step("open_reporting", "Review reporting obligations", "Check linked STEM reporting tasks before filing.", "reporting", null),
      ]),
      definition("h1b_cap_subject", "H-1B Cap-Subject", "Best next path when the role is specialty-occupation aligned and the employer will sponsor.", ["uscis_h1b_specialty", "uscis_h1b_registration", "uscis_cap_gap"], [
        step("open_h1b_dashboard", "Open the H-1B Dashboard", "Use the dedicated dashboard for employer verification, cap-season timing, and I-129 status.", "h1bDashboard", null),
        step("registration", "Confirm cap-season timing", "Use USCIS-published dates only.", null, "https://www.uscis.gov/working-in-the-united-states/temporary-workers/h-1b-specialty-occupations/h-1b-electronic-registration-process"),
        step("policy_alerts", "Monitor reviewed application alerts", "Use the policy feed for filing-process changes.", "policyAlerts?filter=applications", null),
      ]),
      definition("h1b_cap_exempt", "H-1B Cap-Exempt", "Useful when the employer may qualify for the cap-exempt filing route.", ["uscis_h1b_specialty", "uscis_nonimmigrant_stem"], [
        step("open_h1b_dashboard", "Open the H-1B Dashboard", "Use the dedicated dashboard for cap-exempt verification and petition tracking.", "h1bDashboard", null),
        step("verify_employer_type", "Confirm the cap-exempt basis", "The app stores your employer type, but it does not verify it.", null, "https://www.uscis.gov/working-in-the-united-states/h-1b-specialty-occupations?stream=top"),
      ]),
      definition("o1a", "O-1A", "Exploratory route for users with a petitioner or agent plus strong evidence signals.", ["uscis_o1"], [
        step("collect_o1_evidence", "Collect O-1A evidence", "Gather exhibits for awards, publications, judging, press, patents, or similar criteria.", "documentSelection", null),
      ]),
      definition("eb1", "EB-1", "Educational overview only in v1. The app does not predict timing or approval.", ["uscis_eb1", "dos_visa_bulletin_march_2026"], [
        step("review_eb1", "Review EB-1 categories", "Check the official USCIS criteria first.", null, "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-first-preference-eb-1"),
        step("visa_bulletin", "Open the current Visa Bulletin", "Use the March 2026 bulletin as the current reference.", null, "https://travel.state.gov/content/travel/en/legal/visa-law0/visa-bulletin/2026/visa-bulletin-for-march-2026.html"),
      ]),
      definition("eb2_niw", "EB-2 NIW", "Educational overview only in v1. This card highlights the official NIW path without filing advice.", ["uscis_eb2", "dos_visa_bulletin_march_2026"], [
        step("review_eb2", "Review EB-2 and NIW guidance", "Use the USCIS page for the official NIW overview.", null, "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-second-preference-eb-2"),
        step("visa_bulletin", "Open the current Visa Bulletin", "Use the March 2026 bulletin as the current reference.", null, "https://travel.state.gov/content/travel/en/legal/visa-law0/visa-bulletin/2026/visa-bulletin-for-march-2026.html"),
      ]),
      definition("eb2_eb3_employer", "EB-2 / EB-3 Employer-Sponsored", "Educational overview only in v1. Use this to understand PERM and I-140 staging.", ["uscis_eb2", "uscis_eb3", "dol_perm", "dos_visa_bulletin_march_2026"], [
        step("perm", "Review PERM", "Employer-sponsored permanent residence usually starts in DOL FLAG / PERM.", null, "https://flag.dol.gov/programs/perm"),
        step("i140", "Review I-140 categories", "Use the USCIS EB-2 and EB-3 pages for official category details.", null, "https://www.uscis.gov/working-in-the-united-states/permanent-workers/employment-based-immigration-third-preference-eb-3"),
      ]),
    ],
    stemEligibleCipPrefixes: [
      "03.05",
      "04.09",
      "05.13",
      "11.",
      "14.",
      "15.",
      "26.",
      "27.",
      "30.10",
      "30.11",
      "30.19",
      "30.30",
      "40.",
      "41.",
      "45.07",
      "45.09",
      "52.13",
    ],
    h1bSeason: {
      fiscalYear: 2027,
      registrationOpenDate: Date.UTC(2026, 2, 4, 17, 0, 0),
      registrationCloseDate: Date.UTC(2026, 2, 19, 16, 0, 0),
      selectionNoticeDate: null,
      petitionFilingEarliestDate: Date.UTC(2026, 3, 1, 0, 0, 0),
      isPublished: true,
      notes: "USCIS published FY 2027 registration timing: March 4, 2026 at noon ET through March 19, 2026 at noon ET, with selected cap-subject filing beginning April 1, 2026.",
    },
    capGapSummary: "Use official USCIS cap-gap guidance for continuity planning. The planner does not assume cap-gap protection without a qualifying registration or filing path.",
    visaBulletinUrl: "https://travel.state.gov/content/travel/en/legal/visa-law0/visa-bulletin/2026/visa-bulletin-for-march-2026.html",
    policyOverlaySummary: "Critical reviewed policy alerts can downgrade temporary-path recommendations until the alert is reviewed."
  };
}

function source(
  id: string,
  label: string,
  url: string,
  effectiveDate: string,
  summary: string
) {
  return {
    id,
    label,
    url,
    effectiveDate,
    lastReviewedDate: "2026-03-11",
    summary,
  };
}

function definition(
  pathwayId: string,
  title: string,
  summary: string,
  citationIds: string[],
  milestoneTemplates: Array<Record<string, unknown>>
) {
  return {
    pathwayId,
    title,
    summary,
    citationIds,
    milestoneTemplates,
  };
}

function step(
  id: string,
  title: string,
  detail: string,
  route: string | null,
  externalUrl: string | null
) {
  return {
    id,
    title,
    detail,
    route,
    externalUrl,
  };
}
