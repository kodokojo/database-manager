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
package io.kodokojo.database.service.actor.project;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.ProjectRepository;

import java.io.Serializable;
import java.util.Map;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class BrickPropertyToBrickConfigurationActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static final Props PROPS(ProjectRepository projectRepository) {
        if (projectRepository == null) {
            throw new IllegalArgumentException("projectRepository must be defined.");
        }
        return Props.create(BrickPropertyToBrickConfigurationActor.class, projectRepository);
    }

    public BrickPropertyToBrickConfigurationActor(ProjectRepository projectRepository) {
        receive(ReceiveBuilder
                .match(BrickPropertyToBrickConfigurationMsg.class, msg -> {
                    ProjectConfiguration projectConfiguration = projectRepository.getProjectConfigurationById(msg.projectConfigurationIdentifier);
                    if (projectConfiguration != null) {
                        projectConfiguration.getStackConfigurations().stream().filter(s -> s.getName().equals(msg.stackName)).findFirst()
                                .ifPresent(s -> s.getBrickConfigurations().stream().filter(b -> b.getName().equals(msg.brickName)).findFirst()
                                        .ifPresent(b -> b.getProperties().putAll(msg.properties)));
                    }
                    projectRepository.updateProjectConfiguration(projectConfiguration);
                    sender().tell(new BrickPropertyToBrickConfigurationResultMsg(true), self());
                    getContext().stop(self());
                })
                .matchAny(this::unhandled).build());
    }

    public static class BrickPropertyToBrickConfigurationMsg extends EventUserRequestMessage {

        private final String projectConfigurationIdentifier;

        private final String stackName;

        private final String brickName;

        private final Map<String, Serializable> properties;

        public BrickPropertyToBrickConfigurationMsg(User requester, Event request, String projectConfigurationIdentifier, String stackName, String brickName, Map<String, Serializable> properties) {
            super(requester, request);
            requireNonNull(projectConfigurationIdentifier, "projectConfigurationIdentifier must be defined.");
            requireNonNull(properties, "properties must be defined.");
            if (isBlank(stackName)) {
                throw new IllegalArgumentException("stackName must be defined.");
            }
            if (isBlank(brickName)) {
                throw new IllegalArgumentException("brickName must be defined.");
            }
            this.stackName = stackName;
            this.brickName = brickName;
            this.projectConfigurationIdentifier = projectConfigurationIdentifier;
            this.properties = properties;
        }
    }


    public static class BrickPropertyToBrickConfigurationResultMsg {

        private final boolean success;

        public BrickPropertyToBrickConfigurationResultMsg(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }

}
