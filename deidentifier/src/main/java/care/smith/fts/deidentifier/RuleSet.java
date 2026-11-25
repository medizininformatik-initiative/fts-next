package care.smith.fts.deidentifier;

import java.util.List;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

/**
 * What {@link Deidentifier} does with each element. The traversal only asks; whether an element
 * that no rule names is kept or removed is the rule set's decision, so an allow list and a deny
 * list both fit.
 */
@FunctionalInterface
public interface RuleSet {

  /**
   * The rules for one resource. The engine asks once per resource and then per element, so a rule
   * set can match the resource and evaluate its expressions once.
   */
  ResourceRules rulesFor(Resource resource);

  @FunctionalInterface
  interface ResourceRules {

    /**
     * @param path the FHIR path of the element, e.g. {@code [Patient, birthDate]}
     * @param element the element of the input resource, so a rule set can match it by identity
     */
    Rule ruleFor(List<String> path, Base element);
  }
}
