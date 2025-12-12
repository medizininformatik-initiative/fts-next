package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class FhirParameterExtractorTest {

  @Test
  void extractRequiredString_withValidParameter_returnsValue() {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType("test-namespace"));

    String result = FhirParameterExtractor.extractRequiredString(params, "namespace");

    assertThat(result).isEqualTo("test-namespace");
  }

  @Test
  void extractRequiredString_withMissingParameter_throwsException() {
    var params = new Parameters();
    params.addParameter().setName("other").setValue(new StringType("value"));

    assertThatThrownBy(() -> FhirParameterExtractor.extractRequiredString(params, "namespace"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter 'namespace'");
  }

  @Test
  void extractRequiredString_withEmptyValue_throwsException() {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(""));

    assertThatThrownBy(() -> FhirParameterExtractor.extractRequiredString(params, "namespace"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Parameter 'namespace' must not be empty");
  }

  @Test
  void extractRequiredString_withBlankValue_throwsException() {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType("   "));

    assertThatThrownBy(() -> FhirParameterExtractor.extractRequiredString(params, "namespace"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Parameter 'namespace' must not be empty");
  }

  @Test
  void extractAllStrings_withMultipleValues_returnsAll() {
    var params = new Parameters();
    params.addParameter().setName("originalValue").setValue(new StringType("value1"));
    params.addParameter().setName("originalValue").setValue(new StringType("value2"));
    params.addParameter().setName("originalValue").setValue(new StringType("value3"));

    List<String> result = FhirParameterExtractor.extractAllStrings(params, "originalValue");

    assertThat(result).containsExactly("value1", "value2", "value3");
  }

  @Test
  void extractAllStrings_withNoMatchingParameter_returnsEmptyList() {
    var params = new Parameters();
    params.addParameter().setName("other").setValue(new StringType("value"));

    List<String> result = FhirParameterExtractor.extractAllStrings(params, "originalValue");

    assertThat(result).isEmpty();
  }

  @Test
  void extractRequiredStrings_withValues_returnsList() {
    var params = new Parameters();
    params.addParameter().setName("originalValue").setValue(new StringType("value1"));
    params.addParameter().setName("originalValue").setValue(new StringType("value2"));

    List<String> result = FhirParameterExtractor.extractRequiredStrings(params, "originalValue");

    assertThat(result).containsExactly("value1", "value2");
  }

  @Test
  void extractRequiredStrings_withNoValues_throwsException() {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType("test"));

    assertThatThrownBy(() -> FhirParameterExtractor.extractRequiredStrings(params, "originalValue"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At least one 'originalValue' parameter is required");
  }

  @Test
  void addParameter_addsStringParameter() {
    var params = new Parameters();

    FhirParameterExtractor.addParameter(params, "namespace", "test-value");

    assertThat(params.getParameter()).hasSize(1);
    var param = params.getParameter().get(0);
    assertThat(param.getName()).isEqualTo("namespace");
    assertThat(param.getValue().primitiveValue()).isEqualTo("test-value");
  }

  @Test
  void addPart_addsStringPart() {
    var param = new Parameters.ParametersParameterComponent();
    param.setName("pseudonym");

    FhirParameterExtractor.addPart(param, "originalValue", "patient-123");

    assertThat(param.getPart()).hasSize(1);
    var part = param.getPart().get(0);
    assertThat(part.getName()).isEqualTo("originalValue");
    assertThat(part.getValue().primitiveValue()).isEqualTo("patient-123");
  }

  @Test
  void validateIdentifier_withValidValue_returnsValue() {
    String result = FhirParameterExtractor.validateIdentifier("valid-id_123", "testParam");

    assertThat(result).isEqualTo("valid-id_123");
  }

  @Test
  void validateIdentifier_withTooLongValue_throwsException() {
    String tooLong = "a".repeat(257);

    assertThatThrownBy(() -> FhirParameterExtractor.validateIdentifier(tooLong, "testParam"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Parameter 'testParam' exceeds maximum length of 256");
  }

  @Test
  void validateIdentifier_withInvalidCharacters_throwsException() {
    assertThatThrownBy(
            () -> FhirParameterExtractor.validateIdentifier("invalid<>chars", "testParam"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Parameter 'testParam' contains invalid characters");
  }

  @Test
  void validateIdentifiers_withValidValues_returnsList() {
    List<String> values = List.of("id1", "id-2", "id_3");

    List<String> result = FhirParameterExtractor.validateIdentifiers(values, "testParam");

    assertThat(result).containsExactly("id1", "id-2", "id_3");
  }

  @Test
  void validateIdentifiers_withOneInvalidValue_throwsException() {
    List<String> values = List.of("valid", "invalid$char", "also-valid");

    assertThatThrownBy(() -> FhirParameterExtractor.validateIdentifiers(values, "testParam"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Parameter 'testParam' contains invalid characters");
  }
}
