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
package io.kodokojo.database.service.actor.project;

import akka.actor.*;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.model.BootstrapStackData;
import io.kodokojo.commons.model.StackType;
import io.kodokojo.database.service.BootstrapConfigurationProvider;
import io.kodokojo.database.service.ConfigurationStore;
import javaslang.control.Try;
import org.apache.commons.lang.exception.ExceptionUtils;
import scala.concurrent.duration.Duration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class BootstrapStackActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final BootstrapConfigurationProvider bootstrapConfigurationProvider;

    private final ConfigurationStore configurationStore;

    public static Props PROPS(BootstrapConfigurationProvider bootstrapConfigurationProvider, ConfigurationStore configurationStore) {
        requireNonNull(bootstrapConfigurationProvider, "bootstrapConfigurationProvider must be defined.");
        requireNonNull(configurationStore, "configurationStore must be defined.");
        return Props.create(BootstrapStackActor.class, bootstrapConfigurationProvider, configurationStore);
    }

    public BootstrapStackActor(BootstrapConfigurationProvider bootstrapConfigurationProvider, ConfigurationStore configurationStore) {
        this.bootstrapConfigurationProvider = bootstrapConfigurationProvider;
        this.configurationStore = configurationStore;
        receive(ReceiveBuilder.match(BootstrapStackMsg.class, this::onBootstrapStack)
                .matchAny(this::unhandled).build());

    }

    private void onBootstrapStack(BootstrapStackMsg msg) {
        ActorRef sender = sender();
        Try.of(() -> {
            String projectName = msg.projectName;
            String stackName = msg.stackName;
            StackType stackType = msg.stackType;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Bootstrapping project '{}'", msg.projectName);
            }

            int sshPortEntrypoint = 0;
            if (stackType == StackType.BUILD) {
                sshPortEntrypoint = bootstrapConfigurationProvider.provideTcpPortEntrypoint(projectName, stackName);
            }
            BootstrapStackData res = new BootstrapStackData(projectName, sshPortEntrypoint);
            return res;
        }).onFailure(e -> {
            RuntimeException runtimeException = new RuntimeException("Unable to bootstrap new SSH port for project " + msg.projectName, e);
            sender.tell(new BootstrapStackResultMsg(runtimeException), self());
            getContext().stop(self());
        }).andThen(bootstrapStackData -> {
            configurationStore.storeBootstrapStackData(bootstrapStackData);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Bootstrapping project '{}' with value :{}", msg.projectName, bootstrapStackData);
            }
            sender.tell(new BootstrapStackResultMsg(bootstrapStackData), self());
            getContext().stop(self());
        }).onFailure(e -> {
            RuntimeException runtimeException = new RuntimeException("Unable to store bootstrap configuration for project " + msg.projectName, e);
            sender.tell(new BootstrapStackResultMsg(runtimeException), self());
            getContext().stop(self());
        }).get();
    }

    public static class BootstrapStackMsg {

        private final String projectName;

        private final String stackName;

        private final StackType stackType;

        public BootstrapStackMsg(String projectName, String stackName, StackType stackType) {
            if (isBlank(projectName)) {
                throw new IllegalArgumentException("projectName must be defined.");
            }
            if (isBlank(stackName)) {
                throw new IllegalArgumentException("stackName must be defined.");
            }
            if (stackType == null) {
                throw new IllegalArgumentException("stackType must be defined.");
            }
            this.projectName = projectName;
            this.stackName = stackName;
            this.stackType = stackType;
        }
    }


    public static class BootstrapStackResultMsg {

        private final Try<BootstrapStackData> bootstrapStackData;

        public BootstrapStackResultMsg(BootstrapStackData bootstrapStackData) {
            this.bootstrapStackData = Try.success(bootstrapStackData);
        }

        public BootstrapStackResultMsg(RuntimeException e) {
            this.bootstrapStackData = Try.failure(e);
        }

        public Try<BootstrapStackData> getBootstrapStackData() {
            return bootstrapStackData;
        }
    }


}
