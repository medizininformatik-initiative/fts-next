package care.smith.fts.cda.impl;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import care.smith.fts.cda.services.PatientIdResolver;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;

public class EverythingDataSelector implements DataSelector<Bundle> {
  private final Config common;
  private final IGenericClient client;
  private final PatientIdResolver pidResolver;

  public EverythingDataSelector(
          Config common, IGenericClient client, PatientIdResolver patientIdResolver) {
    this.common = common;
    this.client = client;
    this.pidResolver = patientIdResolver;
  }

  @Override
  public Bundle select(ConsentedPatient patient) {
    var params = new Parameters();
    if (!common.ignoreConsent()) {
      if (patient.maxConsentedPeriod().isPresent()) {
        addConsentParams(params, patient.maxConsentedPeriod().get());
      } else {
        throw new IllegalArgumentException(
            "Patient has no consent configured, and ignoreConsent is false.");
      }
    }

    return client
        .operation()
        .onInstance(pidResolver.resolve(patient.id()))
        .named("everything")
        .withParameters(params)
        .returnResourceType(Bundle.class)
        .useHttpGet()
        .execute();
  }

  private void addConsentParams(Parameters params, Period period) {
    params.addParameter("start", formatWithSystemTZ(period.start()));
    params.addParameter("end", formatWithSystemTZ(period.end()));
  }

  private static String formatWithSystemTZ(ZonedDateTime t) {
    return t.format(ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()));
  }
}
