package care.smith.fts.rda.impl;

import static care.smith.fts.rda.impl.FhirStoreBundleSender.hasHttpSuccess;
import static care.smith.fts.rda.impl.FhirStoreBundleSender.validateTransactionResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.rda.BundleSender.Result;
import care.smith.fts.util.error.TransferProcessException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FhirStoreBundleSenderTest {

  @Test
  void destinationIdReturnsPreComputedValue() {
    var sender = new FhirStoreBundleSender(null, null, "https://blaze.example/fhir");
    assertThat(sender.destinationId()).isEqualTo("https://blaze.example/fhir");
  }

  @ParameterizedTest
  @ValueSource(strings = {"200 OK", "201 Created", "204 No Content"})
  void hasHttpSuccessForTwoXxCodes(String status) {
    assertThat(hasHttpSuccess(entryWithStatus(status))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"400 Bad Request", "404 Not Found", "500 Internal Server Error", "100"})
  void hasHttpSuccessReturnsFalseForNonTwoXx(String status) {
    assertThat(hasHttpSuccess(entryWithStatus(status))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"20", "abc"})
  void hasHttpSuccessReturnsFalseForUnparsableStatus(String status) {
    assertThat(hasHttpSuccess(entryWithStatus(status))).isFalse();
  }

  @Test
  void hasHttpSuccessReturnsFalseForEntryWithoutResponse() {
    assertThat(hasHttpSuccess(new BundleEntryComponent())).isFalse();
  }

  @Test
  void hasHttpSuccessReturnsFalseForEntryWithEmptyResponse() {
    // HAPI treats null/empty status as "response not set"; hasResponse() returns false → no success
    assertThat(hasHttpSuccess(entryWithStatus(null))).isFalse();
    assertThat(hasHttpSuccess(entryWithStatus(""))).isFalse();
  }

  @Test
  void validateEmptyBundleSucceeds() {
    create(validateTransactionResponse(new Bundle())).expectNext(new Result()).verifyComplete();
  }

  @Test
  void validateAllSuccessEntriesSucceeds() {
    create(validateTransactionResponse(transactionResponse("200 OK", "201 Created")))
        .expectNext(new Result())
        .verifyComplete();
  }

  @Test
  void validateNonSuccessEntryErrors() {
    create(validateTransactionResponse(transactionResponse("400 Bad Request")))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void validateMixedEntriesErrors() {
    create(validateTransactionResponse(transactionResponse("200 OK", "422 Unprocessable Entity")))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void validateEntryWithoutResponseErrors() {
    var bundle = new Bundle();
    bundle.addEntry();
    create(validateTransactionResponse(bundle))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void validateEntryWithResponseButNoStatusErrors() {
    // Response is non-empty (location set) so hasResponse()=true, but hasStatus()=false
    var response = new Bundle.BundleEntryResponseComponent().setLocation("Patient/123");
    var bundle = new Bundle();
    bundle.addEntry().setResponse(response);
    create(validateTransactionResponse(bundle))
        .expectError(TransferProcessException.class)
        .verify();
  }

  private static BundleEntryComponent entryWithStatus(String status) {
    return new BundleEntryComponent()
        .setResponse(new Bundle.BundleEntryResponseComponent().setStatus(status));
  }

  private static Bundle transactionResponse(String... statusCodes) {
    var responseBundle = new Bundle();
    responseBundle.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
    for (var status : statusCodes) {
      responseBundle
          .addEntry()
          .setResponse(new Bundle.BundleEntryResponseComponent().setStatus(status));
    }
    return responseBundle;
  }
}
