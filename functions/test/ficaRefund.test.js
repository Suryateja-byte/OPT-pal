const test = require("node:test");
const assert = require("node:assert/strict");

process.env.GCLOUD_PROJECT = "opt-pal-test";
process.env.FIREBASE_CONFIG = JSON.stringify({storageBucket: "opt-pal-test.appspot.com"});

const {
  buildFicaEligibilityResult,
  detectAdditionalMedicareComplexity,
  normalizeW2Extraction,
} = require("../lib/ficaRefund");
const {canonicalizeDocumentExtraction} = require("../lib/index");

test("canonicalizeDocumentExtraction stores masked W-2 identifiers only", () => {
  const normalized = canonicalizeDocumentExtraction(
    "W-2 Form",
    {
      employee_ssn: "123-45-6789",
      employer_ein: "12-3456789",
      tax_year: "2025",
      social_security_tax_box4: "210.80",
      medicare_tax_box6: "49.30",
    },
    "Wage and Tax Statement"
  );

  assert.equal(normalized.documentType, "W-2 Form");
  assert.equal(normalized.data.document_type, "w2");
  assert.equal(normalized.data.employee_ssn_last4, "6789");
  assert.equal(normalized.data.employer_ein_masked, "XX-XXX6789");
  assert.equal(normalized.data.employee_ssn, undefined);
  assert.equal(normalized.data.employer_ein, undefined);
});

test("normalizeW2Extraction supports alias keys and state rows", () => {
  const parsed = normalizeW2Extraction({
    documentId: "doc-1",
    fileName: "w2.pdf",
    documentType: "W-2 Form",
    extractedData: {
      employer: "Acme Corp",
      year: "2025",
      employee_ssn: "***-**-6789",
      employer_ein: "12-3456789",
      box_4: "210.80",
      box_6: "49.30",
      state_rows: [
        {
          state_code: "CA",
          wages: "10000.00",
          withholding: "500.00",
        },
      ],
    },
  });

  assert.equal(parsed.documentId, "doc-1");
  assert.equal(parsed.taxYear, 2025);
  assert.equal(parsed.employerName, "Acme Corp");
  assert.equal(parsed.employeeSsnLast4, "6789");
  assert.equal(parsed.employerEinMasked, "XX-XXX6789");
  assert.equal(parsed.socialSecurityTaxBox4, 210.8);
  assert.equal(parsed.stateWageRows.length, 1);
});

test("buildFicaEligibilityResult approves a clear F-1 OPT case", () => {
  const result = buildFicaEligibilityResult({
    profile: {
      optType: "initial",
      firstUsStudentTaxYear: 2022,
    },
    inputs: {
      authorizedEmploymentConfirmed: true,
      maintainedStudentStatusForEntireTaxYear: true,
      noResidencyStatusChangeConfirmed: true,
    },
    w2: {
      documentId: "doc-1",
      fileName: "w2.pdf",
      displayName: "W-2",
      taxYear: 2025,
      employerName: "Acme Corp",
      employerEinMasked: "XX-XXX6789",
      employeeName: "Student",
      employeeSsnLast4: "6789",
      wagesBox1: 10000,
      federalWithholdingBox2: 500,
      socialSecurityWagesBox3: 10000,
      socialSecurityTaxBox4: 620,
      medicareWagesBox5: 10000,
      medicareTaxBox6: 145,
      stateWageRows: [],
    },
    now: Date.UTC(2026, 2, 10),
  });

  assert.equal(result.classification, "eligible");
  assert.equal(result.refundAmount, 765);
});

test("buildFicaEligibilityResult returns not_applicable when no FICA withholding exists", () => {
  const result = buildFicaEligibilityResult({
    profile: {optType: "stem", firstUsStudentTaxYear: 2023},
    inputs: {
      authorizedEmploymentConfirmed: true,
      maintainedStudentStatusForEntireTaxYear: true,
      noResidencyStatusChangeConfirmed: true,
    },
    w2: {
      documentId: "doc-2",
      fileName: "w2.pdf",
      displayName: "W-2",
      taxYear: 2025,
      employerName: "Acme Corp",
      employerEinMasked: "XX-XXX6789",
      employeeName: "Student",
      employeeSsnLast4: "6789",
      wagesBox1: 10000,
      federalWithholdingBox2: 500,
      socialSecurityWagesBox3: 0,
      socialSecurityTaxBox4: 0,
      medicareWagesBox5: 0,
      medicareTaxBox6: 0,
      stateWageRows: [],
    },
    now: Date.UTC(2026, 2, 10),
  });

  assert.equal(result.classification, "not_applicable");
  assert.equal(result.refundAmount, 0);
});

test("buildFicaEligibilityResult requires manual review in the sixth calendar year", () => {
  const result = buildFicaEligibilityResult({
    profile: {optType: "initial", firstUsStudentTaxYear: 2020},
    inputs: {
      authorizedEmploymentConfirmed: true,
      maintainedStudentStatusForEntireTaxYear: true,
      noResidencyStatusChangeConfirmed: true,
    },
    w2: {
      documentId: "doc-3",
      fileName: "w2.pdf",
      displayName: "W-2",
      taxYear: 2025,
      employerName: "Acme Corp",
      employerEinMasked: "XX-XXX6789",
      employeeName: "Student",
      employeeSsnLast4: "6789",
      wagesBox1: 10000,
      federalWithholdingBox2: 500,
      socialSecurityWagesBox3: 10000,
      socialSecurityTaxBox4: 620,
      medicareWagesBox5: 10000,
      medicareTaxBox6: 145,
      stateWageRows: [],
    },
    now: Date.UTC(2026, 2, 10),
  });

  assert.equal(result.classification, "manual_review_required");
});

test("detectAdditionalMedicareComplexity flags suspicious Medicare withholding", () => {
  assert.equal(
    detectAdditionalMedicareComplexity(
      {
        documentId: "doc-4",
        fileName: "w2.pdf",
        displayName: "W-2",
        taxYear: 2025,
        employerName: "Acme Corp",
        employerEinMasked: "XX-XXX6789",
        employeeName: "Student",
        employeeSsnLast4: "6789",
        wagesBox1: 240000,
        federalWithholdingBox2: 1000,
        socialSecurityWagesBox3: 176100,
        socialSecurityTaxBox4: 10918.2,
        medicareWagesBox5: 240000,
        medicareTaxBox6: 3600,
        stateWageRows: [],
      },
      2025
    ),
    true
  );
});
