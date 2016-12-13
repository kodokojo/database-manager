/**
 * Kodo Kojo - Software factory done right
 * Copyright © 2016 Kodo Kojo (infos@kodokojo.io)
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.kodokojo.database.service.actor.project;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.dto.ProjectConfigurationCreationDto;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.ProjectRepository;

import static akka.event.Logging.getLogger;

public class ProjectConfigurationDtoCreatorActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(ProjectRepository projectRepository) {
        if (projectRepository == null) {
            throw new IllegalArgumentException("projectRepository must be defined.");
        }
        return Props.create(ProjectConfigurationDtoCreatorActor.class, projectRepository);
    }

    private ActorRef originalSender;

    private ProjectConfigurationDtoCreateMsg initialMsg;

    public ProjectConfigurationDtoCreatorActor(ProjectRepository projectRepository) {
        receive(ReceiveBuilder.match(ProjectConfigurationDtoCreateMsg.class, msg -> {
            originalSender = sender();
            initialMsg = msg;
            LOGGER.debug("Receive a projectConfigDto to create with project name '{}'.", msg.projectConfigDto.getName());
            ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg projectConfigurationBuildMsg = new ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg(msg.getRequester(),msg.originalEvent(), msg.projectConfigDto);
            getContext().actorSelection(EndpointActor.ACTOR_PATH).tell(projectConfigurationBuildMsg, self());
        }).match(ProjectConfigurationBuilderActor.ProjectConfigurationBuildResultMsg.class, msg -> {
            LOGGER.debug("Receive a projectConfiguration to add to store.");
            ProjectConfiguration projectConfiguration = msg.getProjectConfiguration();
            String projectConfigurationId = projectRepository.addProjectConfiguration(projectConfiguration);
            originalSender.tell(new ProjectConfigurationDtoCreateResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(), projectConfigurationId, projectConfiguration.getName()), self());
        }).matchAny(this::unhandled).build());
    }

    public static class ProjectConfigurationDtoCreateMsg extends EventUserRequestMessage {

        private final ProjectConfigurationCreationDto projectConfigDto;

        private final boolean initialSenderIsEventBus;

        public ProjectConfigurationDtoCreateMsg(User requester, Event request, ProjectConfigurationCreationDto projectConfigurationCreationDto) {
            this(requester, request, projectConfigurationCreationDto, false);
        }

        public ProjectConfigurationDtoCreateMsg(User requester, Event request, ProjectConfigurationCreationDto projectConfigurationCreationDto, boolean initialSenderIsEventBus) {
            super(requester, request);
            if (projectConfigurationCreationDto == null) {
                throw new IllegalArgumentException("projectCreationDto must be defined.");
            }
            this.projectConfigDto = projectConfigurationCreationDto;
            this.initialSenderIsEventBus = initialSenderIsEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return initialSenderIsEventBus;
        }
    }

    public static class ProjectConfigurationDtoCreateResultMsg extends EventUserReplyMessage {

        private final String projectConfigurationId;

        private final String projectName;

        public ProjectConfigurationDtoCreateResultMsg(User requester, Event request, String projectConfigurationId, String projectName) {
            super(requester, request, Event.PROJECTCONFIG_CREATION_REPLY, projectConfigurationId);
            this.projectConfigurationId = projectConfigurationId;
            this.projectName = projectName;
        }

        public String getProjectConfigurationId() {
            return projectConfigurationId;
        }

        public String getProjectName() {
            return projectName;
        }
    }
}
