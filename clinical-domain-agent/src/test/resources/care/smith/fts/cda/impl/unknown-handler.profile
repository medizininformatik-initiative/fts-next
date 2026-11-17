deidentiFHIR.profile.version=0.2
modules {
  patient {
    base = ["Patient.id"]
    paths { "Patient.id" { handler = conditionalReferencesReplacementHandler } }
    pattern = "Patient.exists()"
  }
}
