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

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.ProjectRepository;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class ProjectConfigurationUpdaterActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static final Props PROPS(ProjectRepository projectRepository) {
        requireNonNull(projectRepository, "projectRepository must be defined.");
        return Props.create(ProjectConfigurationUpdaterActor.class, projectRepository);
    }

    public ProjectConfigurationUpdaterActor(ProjectRepository projectRepository) {
        receive(ReceiveBuilder
                .match(ProjectConfigurationUpdaterMsg.class, msg -> {
                    // Ask for right if UserRequester is defined.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Update ProjectConfiguration {}", msg.projectConfiguration);
                    }
                    projectRepository.updateProjectConfiguration(msg.projectConfiguration);
                    sender().tell(new ProjectConfigurationUpdaterResultMsg(msg.getRequester(),msg.originalEvent(), msg.projectConfiguration), self());
                    getContext().stop(self());
                })
                .matchAny(this::unhandled).build());
    }

    public static class ProjectConfigurationUpdaterMsg extends EventUserRequestMessage {

        private final ProjectConfiguration projectConfiguration;

        public ProjectConfigurationUpdaterMsg(User requester, Event request, ProjectConfiguration projectConfiguration) {
            super(requester, request);
            if (projectConfiguration == null) {
                throw new IllegalArgumentException("projectConfiguration must be defined.");
            }
            this.projectConfiguration = projectConfiguration;
        }
    }
    public static class ProjectConfigurationUpdaterResultMsg extends EventUserRequestMessage {

        private final ProjectConfiguration projectConfiguration;

        public ProjectConfigurationUpdaterResultMsg(User requester, Event request,ProjectConfiguration projectConfiguration) {
            super(requester, request);
            if (projectConfiguration == null) {
                throw new IllegalArgumentException("projectConfiguration must be defined.");
            }
            this.projectConfiguration = projectConfiguration;
        }

        public ProjectConfiguration getProjectConfiguration() {
            return projectConfiguration;
        }
    }
}
