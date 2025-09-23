{
  deidentiFHIR.profile.version=0.2
  modules = {
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_patient: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/patient/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_fall-1_0_1_kontakt: {include required("de.medizininformatikinitiative.kerndatensatz.fall-1.0.1/kontakt/TransportToRD.conf")}
}}
