package care.smith.fts.rda.impl;

import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.util.tca.SecureMappingResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class IdMapperStep implements Deidentificator {
  private final WebClient tcaClient;
  private final MeterRegistry meterRegistry;

  IdMapperStep(WebClient tcaClient, MeterRegistry meterRegistry) {
    this.tcaClient = tcaClient;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Bundle> deidentify(TransportBundle transportBundle) {
    return fetchSecureMapping(transportBundle.transferId())
        .map(
            response -> {
              var bundle = transportBundle.bundle();
              processEntries(bundle, response.tidPidMap(), response.dateShiftMap());
              return bundle;
            })
        .doOnNext(b -> log.trace("Total bundle entries: {}", b.getEntry().size()))
        .switchIfEmpty(
            Mono.fromRunnable(
                () ->
                    log.warn(
                        "Empty secure mapping response for transferId={}, skipping"
                            + " deidentification",
                        transportBundle.transferId())));
  }

  private Mono<SecureMappingResponse> fetchSecureMapping(String transferId) {
    return tcaClient
        .post()
        .uri("/api/v2/rd/secure-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(transferId)
        .retrieve()
        .bodyToMono(SecureMappingResponse.class)
        .retryWhen(defaultRetryStrategy(meterRegistry, "fetchSecureMapping"))
        .doOnError(
            e -> log.error("Unable to resolve transport IDs for transferId={}", transferId, e));
  }

  private void processEntries(
      Bundle bundle, Map<String, String> idMapping, Map<String, String> dateShiftMap) {
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Objects::nonNull)
        .forEach(
            resource -> {
              if (resource instanceof Bundle innerBundle) {
                processEntries(innerBundle, idMapping, dateShiftMap);
              } else {
                replaceResourceId(resource, idMapping);
                walkElements(resource, idMapping, dateShiftMap);
              }
            });
  }

  private void replaceResourceId(Resource resource, Map<String, String> idMapping) {
    var id = resource.getIdPart();
    if (id != null) {
      var replacement = idMapping.get(id);
      if (replacement != null) {
        resource.setId(resource.getResourceType() + "/" + replacement);
      }
    }
  }

  private void walkElements(
      Base element, Map<String, String> idMapping, Map<String, String> dateShiftMap) {
    element.children().stream()
        .flatMap(property -> property.getValues().stream())
        .forEach(value -> processValue(value, idMapping, dateShiftMap));
  }

  private void processValue(
      Base value, Map<String, String> idMapping, Map<String, String> dateShiftMap) {
    if (value instanceof Reference ref) {
      replaceReference(ref, idMapping);
    } else if (value instanceof Identifier ident) {
      replaceIdentifierValue(ident, idMapping);
    } else if (value instanceof BaseDateTimeType dateTime) {
      restoreDateIfNeeded(dateTime, dateShiftMap);
      return;
    }
    walkElements(value, idMapping, dateShiftMap);
  }

  private void replaceReference(Reference ref, Map<String, String> idMapping) {
    var reference = ref.getReference();
    if (reference != null && reference.contains("/")) {
      var slashIndex = reference.lastIndexOf('/');
      var prefix = reference.substring(0, slashIndex);
      var id = reference.substring(slashIndex + 1);
      var replacement = idMapping.get(id);
      if (replacement != null) {
        ref.setReference(prefix + "/" + replacement);
      }
    }
  }

  private void replaceIdentifierValue(Identifier ident, Map<String, String> idMapping) {
    var value = ident.getValue();
    if (value != null) {
      var replacement = idMapping.get(value);
      if (replacement != null) {
        ident.setValue(replacement);
      }
    }
  }

  private void restoreDateIfNeeded(
      BaseDateTimeType dateTimeType, Map<String, String> dateShiftMap) {
    var extension = dateTimeType.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL);
    if (extension == null) return;

    resolveShiftedDate(extension, dateShiftMap).ifPresent(dateTimeType::setValueAsString);
    dateTimeType.removeExtension(DATE_SHIFT_EXTENSION_URL);
  }

  private Optional<String> resolveShiftedDate(
      Extension extension, Map<String, String> dateShiftMap) {
    if (!(extension.getValue() instanceof StringType stringType)) {
      log.warn(
          "Expected StringType for date shift extension but found {}",
          extension.getValue() != null ? extension.getValue().getClass().getSimpleName() : "null");
      return Optional.empty();
    }
    var tId = stringType.getValue();
    var shiftedDate = dateShiftMap.get(tId);
    if (shiftedDate == null) {
      log.warn("Date shift tID '{}' not found in dateShiftMap, skipping", tId);
    }
    return Optional.ofNullable(shiftedDate);
  }
}
