package io.kodokojo.database.config;

import io.kodokojo.commons.config.properties.Key;
import io.kodokojo.commons.config.properties.PropertyConfig;

public interface DatabaseConfig extends PropertyConfig {

    @Key(value = "configurationStore.provider", defaultValue = "zookeeper")
    String configurationStore();

}
