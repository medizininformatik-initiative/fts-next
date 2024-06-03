package care.smith.fts.cda;

import static java.util.Arrays.stream;

import care.smith.fts.api.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class TransferProcessFactory {

  private final ApplicationContext applicationContext;
  private final ObjectMapper objectMapper;

  public TransferProcessFactory(ApplicationContext applicationContext, ObjectMapper objectMapper) {
    this.applicationContext = applicationContext;
    this.objectMapper = objectMapper;
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @SuppressWarnings("unchecked")
  public TransferProcess create(TransferProcessConfig processDefinition) {
    CohortSelector cohortSelector =
        findImpl(
            CohortSelector.class,
            CohortSelector.Factory.class,
            CohortSelector.Config.class,
            processDefinition.getCohortSelector());
    DataSelector dataSelector =
        findImpl(
            DataSelector.class,
            DataSelector.Factory.class,
            DataSelector.Config.class,
            processDefinition.getDataSelector());
    DeidentificationProvider deidentificationProvider =
        findImpl(
            DeidentificationProvider.class,
            DeidentificationProvider.Factory.class,
            DeidentificationProvider.Config.class,
            processDefinition.getDeidentificationProvider());
    BundleSender bundleSender =
        findImpl(
            BundleSender.class,
            BundleSender.Factory.class,
            BundleSender.Config.class,
            processDefinition.getBundleSender());
    return new TransferProcess(
        cohortSelector, dataSelector, deidentificationProvider, bundleSender);
  }

  private <TYPE, CC, IC, FACTORY extends StepFactory<TYPE, CC, IC>> TYPE findImpl(
      Class<TYPE> stepClass,
      Class<FACTORY> factoryClass,
      Class<CC> commonConfigClass,
      Map<String, Object> config) {

    var commonConfigEntries = commonConfigEntries(commonConfigClass);
    var configEntries = config.entrySet();
    var implementations =
        configEntries.stream().filter(e -> !commonConfigEntries.contains(e.getKey())).toList();

    if (implementations.isEmpty()) {
      throw noImplInConfig(factoryClass);
    } else if (implementations.size() > 1) {
      throw multipleImplsInConfig(
          stepClass, implementations.stream().map(Map.Entry::getKey).toList());
    } else {
      return instantiate(stepClass, factoryClass, commonConfigClass, implementations.get(0));
    }
  }

  private static IllegalArgumentException multipleImplsInConfig(
      Class<?> stepClass, List<String> implementations) {
    return new IllegalArgumentException(
        "Multiple config entries look like an implementation for %s: %s"
            .formatted(stepClass.getSimpleName(), implementations));
  }

  private static IllegalArgumentException noImplInConfig(Class<?> stepClass) {
    return new IllegalArgumentException(
        "No config entry looks like an implementation for %s".formatted(stepClass.getSimpleName()));
  }

  private static <CC> Set<String> commonConfigEntries(Class<CC> commonConfigClass) {
    var fields = stream(commonConfigClass.getFields()).map(Field::getName);
    var components = stream(commonConfigClass.getRecordComponents()).map(RecordComponent::getName);
    return Streams.concat(fields, components).collect(Collectors.toSet());
  }

  private <STEPTYPE, CC, IC, FACTORY extends StepFactory<STEPTYPE, CC, IC>> STEPTYPE instantiate(
      Class<STEPTYPE> stepClass,
      Class<FACTORY> factoryClass,
      Class<CC> commonConfigClass,
      Map.Entry<String, Object> config) {
    String implName = config.getKey();
    try {
      FACTORY factoryImpl =
          applicationContext.getBean(implName + stepClass.getSimpleName(), factoryClass);
      IC implConfig = objectMapper.convertValue(config.getValue(), factoryImpl.getConfigType());
      CC commonConfig = objectMapper.convertValue(config.getValue(), commonConfigClass);
      return stepClass.cast(factoryImpl.create(commonConfig, implConfig));
    } catch (NoSuchBeanDefinitionException e) {
      throw noImplFound(stepClass, implName);
    }
  }

  private static IllegalArgumentException noImplFound(Class<?> stepClass, String implName) {
    return new IllegalArgumentException(
        "Implementation %s could not be found for %s"
            .formatted(implName, stepClass.getSimpleName()));
  }
}
