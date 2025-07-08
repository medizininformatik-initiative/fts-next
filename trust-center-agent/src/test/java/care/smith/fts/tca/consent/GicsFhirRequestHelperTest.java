package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirRequestHelper.nextLink;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class GicsFhirRequestHelperTest {

  @Test
  void testNextLinkPreservesBasePath() {
    var requestUrl =
        UriComponentsBuilder.fromUriString("http://example.com/fts")
            .queryParam("existingParam", "value");
    var pagingParams = new PagingParams(10, 20); // from=10, count=20
    var path = "/api/v2/consents";
    var expectedUrl = "http://example.com/fts/api/v2/consents?existingParam=value&from=30&count=20";

    var nextLink = nextLink(requestUrl, pagingParams, path);
    assertThat(nextLink.getUrl())
        .as("nextLink should preserve the base path '/fts' and append the new path")
        .isEqualTo(expectedUrl);
  }

  @Test
  void testNextLinkWithMultiplePathSegments() {
    var requestUrl = UriComponentsBuilder.fromUriString("http://example.com/fts/tca/v1");
    var pagingParams = new PagingParams(0, 10); // from=0, count=10
    var path = "/api/v2/patients/consents";
    var expectedUrl = "http://example.com/fts/tca/v1/api/v2/patients/consents?from=10&count=10";

    var nextLink = nextLink(requestUrl, pagingParams, path);
    assertThat(nextLink.getUrl())
        .as("nextLink should preserve all base path segments")
        .isEqualTo(expectedUrl);
  }

  @Test
  void testNextLinkWithEmptyBasePath() {
    var requestUrl = UriComponentsBuilder.fromUriString("http://example.com");
    var pagingParams = new PagingParams(5, 15); // from=5, count=15
    var path = "/api/v2/consents";
    var expectedUrl = "http://example.com/api/v2/consents?from=20&count=15";

    var nextLink = nextLink(requestUrl, pagingParams, path);
    assertThat(nextLink.getUrl())
        .as("nextLink should work correctly even with no base path")
        .isEqualTo(expectedUrl);
  }

  @Test
  void testNextLinkPaginationCalculation() {
    var requestUrl = UriComponentsBuilder.fromUriString("http://example.com/fts");
    var pagingParams = new PagingParams(25, 50); // from=25, count=50
    var path = "/api/v2/consents";

    var nextLink = nextLink(requestUrl, pagingParams, path);
    var actualUrl = nextLink.getUrl();
    assertThat(actualUrl).as("from parameter should be sum (25+50=75)").contains("from=75");
    assertThat(actualUrl).as("count parameter should be preserved as 50").contains("count=50");
  }
}
