const test = require("node:test");
const assert = require("node:assert/strict");
const { PDFDocument } = require("pdf-lib");

const {
  buildI983AssistantBundle,
  buildOfficialI983Pdf,
  normalizeNarrative,
} = require("../lib/i983Assistant");

test("buildI983AssistantBundle exposes template metadata and official sources", () => {
  const bundle = buildI983AssistantBundle();

  assert.equal(bundle.version, "2026-03-10");
  assert.equal(bundle.templateVersion, "ice-i983-2026-03");
  assert.match(bundle.templateSha256, /^[a-f0-9]{64}$/);
  assert.ok(bundle.sources.some((source) => source.url.includes("studyinthestates.dhs.gov")));
  assert.ok(bundle.sources.some((source) => source.url.includes("ice.gov/doclib/sevis/pdf/i983.pdf")));
});

test("normalizeNarrative sanitizes fields and preserves consult classification", () => {
  const normalized = normalizeNarrative({
    classification: "consult_dso_attorney",
    confidence: "high",
    studentRole: "  Software engineer  ",
    goalsAndObjectives: "Build systems\nwith measurable outcomes.",
    employerOversight: "Weekly check-ins",
    measuresAndAssessments: "Code review",
    annualEvaluation: "Annual review",
    finalEvaluation: "Final review",
    missingInputs: ["role_to_major_fit", "hours_per_week"],
    warnings: ["Check the employer relationship."],
  });

  assert.equal(normalized.classification, "consult_dso_attorney");
  assert.equal(normalized.confidence, "high");
  assert.equal(normalized.studentRole, "Software engineer");
  assert.equal(normalized.goalsAndObjectives, "Build systems with measurable outcomes.");
  assert.deepEqual(normalized.missingInputs, ["role_to_major_fit", "hours_per_week"]);
});

test("buildOfficialI983Pdf populates core official form fields", async () => {
  const pdfBytes = await buildOfficialI983Pdf({
    studentSection: {
      studentName: "Student Name",
      studentEmailAddress: "student@example.com",
      schoolRecommendingStemOpt: "Example University",
      schoolWhereDegreeWasEarned: "Example University",
      sevisSchoolCode: "ABC214F00123000",
      dsoNameAndContact: "DSO Name, dso@example.edu",
      studentSevisId: "N0012345678",
      requestedStartDate: Date.UTC(2026, 5, 1),
      requestedEndDate: Date.UTC(2028, 4, 31),
      qualifyingMajorAndCipCode: "Computer Science (11.0701)",
      degreeLevel: "Bachelor's",
      degreeAwardedDate: Date.UTC(2025, 4, 15),
      employmentAuthorizationNumber: "A123456789",
    },
    employerSection: {
      employerName: "Acme Corp",
      streetAddress: "123 Main St",
      employerWebsiteUrl: "https://example.com",
      city: "Austin",
      state: "TX",
      zipCode: "78701",
      employerEin: "12-3456789",
      fullTimeEmployeesInUs: "100",
      naicsCode: "541511",
      hoursPerWeek: 40,
      salaryAmountAndFrequency: "$100,000 annually",
      employmentStartDate: Date.UTC(2026, 5, 1),
      employerOfficialNameAndTitle: "Manager, Director",
      employingOrganizationName: "Acme Corp",
    },
    trainingPlanSection: {
      siteName: "HQ",
      siteAddress: "123 Main St, Austin, TX 78701",
      officialName: "Manager",
      officialTitle: "Director",
      officialEmail: "manager@example.com",
      officialPhoneNumber: "555-1212",
      studentRole: "Software engineer",
      goalsAndObjectives: "Build reliable systems.",
      employerOversight: "Weekly check-ins.",
      measuresAndAssessments: "Sprint reviews.",
      additionalRemarks: "None.",
    },
    evaluationSection: {
      annualEvaluationFromDate: Date.UTC(2026, 5, 1),
      annualEvaluationToDate: Date.UTC(2027, 4, 31),
      finalEvaluationFromDate: Date.UTC(2026, 5, 1),
      finalEvaluationToDate: Date.UTC(2028, 4, 31),
    },
  });

  const pdf = await PDFDocument.load(pdfBytes);
  const form = pdf.getForm();
  assert.equal(form.getTextField("Student Email Address").getText(), "student@example.com");
  assert.equal(form.getTextField("Employer Name").getText(), "Acme Corp");
  assert.equal(form.getTextField("Site Name").getText(), "HQ");
  assert.equal(form.getTextField("undefined_3").getText(), "06/01/2026");
});
