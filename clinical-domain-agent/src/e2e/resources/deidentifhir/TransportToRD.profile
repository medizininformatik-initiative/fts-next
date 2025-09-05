{
  deidentiFHIR.profile.version=0.2
  modules = {
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_wert: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/wert/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_befund: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/befund/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_anforderung: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/anforderung/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_medikation: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/medikation/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_verordnung: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/verordnung/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_verabreichung: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/verabreichung/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_patient: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/patient/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_vitalstatus: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/vitalstatus/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_proband: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/proband/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_fall-1_0_1_kontakt: {include required("de.medizininformatikinitiative.kerndatensatz.fall-1.0.1/kontakt/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_diagnose-1_0_4_diagnose: {include required("de.medizininformatikinitiative.kerndatensatz.diagnose-1.0.4/diagnose/TransportToRD.conf")}
    de_medizininformatikinitiative_kerndatensatz_prozedur-1_0_7_procedure: {include required("de.medizininformatikinitiative.kerndatensatz.prozedur-1.0.7/procedure/TransportToRD.conf")}
}}
