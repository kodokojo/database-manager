/**
 * Kodo Kojo - Microservice which allow to access to Database.
 * Copyright © 2016 Kodo Kojo (infos@kodokojo.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.database.config.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.commons.config.MicroServiceConfig;
import io.kodokojo.commons.config.RedisConfig;
import io.kodokojo.commons.config.VersionConfig;
import io.kodokojo.commons.config.ZookeeperConfig;
import io.kodokojo.commons.config.properties.PropertyConfig;
import io.kodokojo.commons.config.properties.PropertyResolver;
import io.kodokojo.commons.config.properties.provider.PropertyValueProvider;
import io.kodokojo.commons.model.ServiceInfo;
import io.kodokojo.database.service.BootstrapConfigurationProvider;
import io.kodokojo.database.service.ConfigurationStore;
import io.kodokojo.database.service.zookeeper.ZookeeperBootstrapConfigurationProvider;
import io.kodokojo.database.service.zookeeper.ZookeeperConfigurationStore;

public class ZookeeperModule extends AbstractModule {
    @Override
    protected void configure() {
        // Nothing to do.
    }

    @Provides
    @Singleton
    ConfigurationStore provideConfigurationStore(ZookeeperConfig zookeeperConfig) {
        return new ZookeeperConfigurationStore(zookeeperConfig);
    }

    @Provides
    @Singleton
    BootstrapConfigurationProvider provideBootstrapConfigurationProvider(ZookeeperConfig zookeeperConfig) {
        return new ZookeeperBootstrapConfigurationProvider(zookeeperConfig);
    }


    @Provides
    @Singleton
    ZookeeperConfig provideZookeeperConfig(PropertyValueProvider valueProvider) {
        return createConfig(ZookeeperConfig.class, valueProvider);
    }

    private <T extends PropertyConfig> T createConfig(Class<T> configClass, PropertyValueProvider valueProvider) {
        PropertyResolver resolver = new PropertyResolver(valueProvider);
        return resolver.createProxy(configClass);
    }
}
