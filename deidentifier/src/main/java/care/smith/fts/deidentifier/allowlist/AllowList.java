package care.smith.fts.deidentifier.allowlist;

import care.smith.fts.deidentifier.Deidentifier;
import care.smith.fts.deidentifier.Registry;
import com.typesafe.config.Config;

/**
 * Builds a {@link Deidentifier} from an allow-list {@link Profile} in HOCON: only the elements that
 * a module lists in its {@code base} survive.
 */
public interface AllowList {

  static Deidentifier fromConfig(Config config) {
    return fromConfig(config, new Registry());
  }

  static Deidentifier fromConfig(Config config, Registry registry) {
    return new Deidentifier(HoconProfile.parse(config, registry));
  }
}
