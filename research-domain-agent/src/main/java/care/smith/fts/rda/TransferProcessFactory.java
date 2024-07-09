package care.smith.fts.rda;

import static java.util.Arrays.stream;

import care.smith.fts.api.*;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.DeidentificationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransferProcessFactory {

  private final ApplicationContext context;
  private final ObjectMapper objectMapper;

  public TransferProcessFactory(
      ApplicationContext context,
      @Qualifier("transferProcessObjectMapper") ObjectMapper objectMapper) {
    this.context = context;
    this.objectMapper = objectMapper;
  }

  @SuppressWarnings("unchecked")
  public TransferProcess create(TransferProcessConfig processDefinition, String project) {
    log.debug("Create TransferProcess from definition: {}", processDefinition);
    DeidentificationProvider deidentificationProvider =
        instantiateImpl(
            DeidentificationProvider.class,
            DeidentificationProvider.Factory.class,
            DeidentificationProvider.Config.class,
            processDefinition.deidentificationProvider());
    BundleSender bundleSender =
        instantiateImpl(
            BundleSender.class,
            BundleSender.Factory.class,
            BundleSender.Config.class,
            processDefinition.bundleSender());
    return new TransferProcess(project, deidentificationProvider, bundleSender);
  }

  private <TYPE, CC, IC, FACTORY extends TransferProcessStepFactory<TYPE, CC, IC>>
      TYPE instantiateImpl(
          Class<TYPE> stepClass,
          Class<FACTORY> factoryClass,
          Class<CC> commonConfigClass,
          @NotNull Map<String, ?> config) {

    var impl = findImpl(stepClass, factoryClass, commonConfigClass, config);
    return instantiate(stepClass, factoryClass, commonConfigClass, impl);
  }

  private static <TYPE, CC, IC, FACTORY extends TransferProcessStepFactory<TYPE, CC, IC>>
      Entry<String, ?> findImpl(
          Class<TYPE> stepClass,
          Class<FACTORY> factoryClass,
          Class<CC> commonConfigClass,
          Map<String, ?> config) {
    var commonConfigEntries = commonConfigEntries(commonConfigClass);
    var configEntries = config.entrySet();
    var implementations =
        configEntries.stream().filter(e -> !commonConfigEntries.contains(e.getKey())).toList();

    checkImplementationFound(factoryClass, implementations);
    checkOnlyOneImplementation(stepClass, implementations);
    return implementations.get(0);
  }

  private static <TYPE> void checkOnlyOneImplementation(
      Class<TYPE> stepClass, List<? extends Entry<String, ?>> implementations) {
    if (implementations.size() > 1) {
      throw new IllegalArgumentException(
          "Multiple config entries look like an implementation for %s: %s"
              .formatted(
                  stepClass.getSimpleName(), implementations.stream().map(Entry::getKey).toList()));
    }
  }

  private static <TYPE, CC, IC, FACTORY extends TransferProcessStepFactory<TYPE, CC, IC>>
      void checkImplementationFound(
          Class<FACTORY> factoryClass, List<? extends Entry<String, ?>> implementations) {
    if (implementations.isEmpty()) {
      throw new IllegalArgumentException(
          "No config entry looks like an implementation for %s"
              .formatted(factoryClass.getSimpleName()));
    }
  }

  private static <CC> Set<String> commonConfigEntries(Class<CC> commonConfigClass) {
    var fields = stream(commonConfigClass.getFields()).map(Field::getName);
    var components = stream(commonConfigClass.getRecordComponents()).map(RecordComponent::getName);
    return Streams.concat(fields, components).collect(Collectors.toSet());
  }

  private <STEPTYPE, CC, IC, FACTORY extends TransferProcessStepFactory<STEPTYPE, CC, IC>>
      STEPTYPE instantiate(
          Class<STEPTYPE> stepClass,
          Class<FACTORY> factoryClass,
          Class<CC> commonConfigClass,
          Entry<String, ?> config) {
    String implName = config.getKey();
    try {
      FACTORY factoryImpl = context.getBean(implName + stepClass.getSimpleName(), factoryClass);
      CC commonConfig = createConfig(commonConfigClass, config);
      IC implConfig = createConfig(factoryImpl.getConfigType(), config);
      return stepClass.cast(factoryImpl.create(commonConfig, implConfig));
    } catch (NoSuchBeanDefinitionException e) {
      throw new IllegalArgumentException(
          "Implementation %s could not be found for %s"
              .formatted(implName, stepClass.getSimpleName()));
    }
  }

  private <C> C createConfig(Class<C> configClass, Entry<String, ?> config) {
    try {
      return objectMapper.convertValue(config.getValue(), configClass);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid config entry '%s', \n%s".formatted(config.getKey(), config.getValue()), e);
    }
  }
}
