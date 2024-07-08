package care.smith.fts.cda.rest.it;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.BaseIT;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.rest.it.mock.MockBundleSender;
import care.smith.fts.cda.rest.it.mock.MockCohortSelector;
import care.smith.fts.cda.rest.it.mock.MockDataSelector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;

/*
 * CDA has two endpoints to test: `/{project}/start` and `/status/{projectId}`.
 * The URL of the latter is part of the response of the first.
 *
 * The `start` endpoint does:
 * 1. Request ConsentedPatients from TCA
 *   a. Filter ConsentedPatients by date range
 * 2. Request FhirId from HDS
 * 3. Request everything from HDS
 * 4. Request transport ids from TCA
 *   a. Deidentify Patient
 * 5. Send Patient to RDA
 *
 * Things that can go wrong:
 * - [x] Invalid project
 * - [x] CohortSelector
 *   - [x] TCA slow
 *   - [x] TCA down
 *   - [x] Wrong content type
 *   - [x] TCA/gICS: unknown domain, this is the only setting that returns an error (bad request),
 * other settings may return an empty bundle
 * - [ ] DataSelector
 *   - [ ] HDS slow
 *   - [ ] HDS down
 *   - [ ] FhirResolveService
 *     - [ ] wrong content type
 *     - [ ] may return error:
 *       - [ ] More than one result
 *       - [ ] Unable to resolve patient id
 *   - [ ] everything
 *     - [ ] wrong content type
 *     - [ ] paging - not implemented
 * - [ ] Deidentifhir
 *   - [ ] TCA slow or down
 *   - [ ] gPAS unknown domain -> bad request
 * - [ ] BundleSender
 *   - [ ] RDA slow or down
 */
@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  protected WebClient client;

  ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

  MockCohortSelector mockCohortSelector = new MockCohortSelector(tca);
  MockDataSelector mockDataSelector = new MockDataSelector(tca, hds);
  MockBundleSender mockBundleSender = new MockBundleSender(rda);

  protected static final String DEFAULT_IDENTIFIER_SYSTEM = "http://fts.smith.care";

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @AfterEach
  void tearDown() {
    resetAll();
  }
}

// Wrong domain ret from gics
// {
//  "resourceType": "OperationOutcome",
//  "issue": [
//    {
//      "severity": "error",
//      "code": "processing",
//      "diagnostics": "No consents found for domain  'MII333'."
//    }
//  ]
// }
