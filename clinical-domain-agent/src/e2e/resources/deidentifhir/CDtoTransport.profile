{
  deidentiFHIR.profile.version=0.2
  modules = {
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_wert: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/wert/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_befund: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/befund/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_laborbefund-1_0_6_anforderung: {include required("de.medizininformatikinitiative.kerndatensatz.laborbefund-1.0.6/anforderung/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_medikation: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/medikation/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_verordnung: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/verordnung/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_medikation-1_0_10_verabreichung: {include required("de.medizininformatikinitiative.kerndatensatz.medikation-1.0.10/verabreichung/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_patient: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/patient/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_vitalstatus: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/vitalstatus/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_person-1_0_14_proband: {include required("de.medizininformatikinitiative.kerndatensatz.person-1.0.14/proband/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_fall-1_0_1_kontakt: {include required("de.medizininformatikinitiative.kerndatensatz.fall-1.0.1/kontakt/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_diagnose-1_0_4_diagnose: {include required("de.medizininformatikinitiative.kerndatensatz.diagnose-1.0.4/diagnose/CDtoTransport.conf")}
    de_medizininformatikinitiative_kerndatensatz_prozedur-1_0_7_procedure: {include required("de.medizininformatikinitiative.kerndatensatz.prozedur-1.0.7/procedure/CDtoTransport.conf")}
}}
