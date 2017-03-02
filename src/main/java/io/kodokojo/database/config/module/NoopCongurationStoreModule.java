/**
 * Kodo Kojo - Microservice which allow to access to Database.
 * Copyright © 2017 Kodo Kojo (infos@kodokojo.io)
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
import io.kodokojo.commons.config.ZookeeperConfig;
import io.kodokojo.commons.config.properties.PropertyConfig;
import io.kodokojo.commons.config.properties.PropertyResolver;
import io.kodokojo.commons.config.properties.provider.PropertyValueProvider;
import io.kodokojo.commons.model.BootstrapStackData;
import io.kodokojo.commons.service.ssl.SSLKeyPair;
import io.kodokojo.database.service.BootstrapConfigurationProvider;
import io.kodokojo.database.service.ConfigurationStore;
import io.kodokojo.database.service.zookeeper.ZookeeperBootstrapConfigurationProvider;
import io.kodokojo.database.service.zookeeper.ZookeeperConfigurationStore;

public class NoopCongurationStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        // Nothing to do.
    }

    @Provides
    @Singleton
    ConfigurationStore provideConfigurationStore() {
        return new ConfigurationStore() {
            @Override
            public boolean storeBootstrapStackData(BootstrapStackData bootstrapStackData) {
                return true;
            }

            @Override
            public boolean storeSSLKeys(String projectName, String brickTypeName, SSLKeyPair sslKeyPair) {
                return true;
            }
        };
    }

    @Provides
    @Singleton
    BootstrapConfigurationProvider provideBootstrapConfigurationProvider() {
        return (projectName, stackName) -> 0;
    }

}
