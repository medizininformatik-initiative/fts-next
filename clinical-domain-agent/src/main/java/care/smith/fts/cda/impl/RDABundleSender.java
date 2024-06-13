package care.smith.fts.cda.impl;

import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import care.smith.fts.api.BundleSender;
import java.io.*;

import care.smith.fts.api.ConsentedPatient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class RDABundleSender implements BundleSender<Bundle> {
  private final RDABundleSenderConfig config;
  private final CloseableHttpClient client;
  private final FhirContext fhir;

  public RDABundleSender(
      RDABundleSenderConfig config, CloseableHttpClient client, FhirContext fhir) {
    this.fhir = fhir;
    this.config = config;
    this.client = client;
  }

  @Override
  public Mono<Result> send(Flux<Bundle> bundle, ConsentedPatient patient) {
    IParser parser = fhir.newJsonParser();
    var request = new HttpPost(config.server().baseUrl() + "/api/v2/process/" + project);

    try (var outStream = new ByteArrayOutputStream()) {
      writeBundle(bundle, parser, outStream);
      return sendStream(request, outStream);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to write to outputstream", e);
    }
  }

  private static void writeBundle(Bundle bundle, IParser parser, ByteArrayOutputStream outStream) {
    try {
      parser.encodeToWriter(bundle, new OutputStreamWriter(outStream));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to write entity to request", e);
    }
  }

  private Boolean sendStream(HttpPost request, ByteArrayOutputStream outStream) {
    try {
      request.setEntity(new ByteArrayEntity(outStream.toByteArray(), APPLICATION_JSON));
      return client.execute(request, r -> r.getCode() < 300);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to send bundle", e);
    }
  }
}
