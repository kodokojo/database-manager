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
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.Project;
import io.kodokojo.commons.model.ProjectBuilder;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventReplyableMessage;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.actor.right.RightEndpointActor;
import io.kodokojo.commons.service.repository.ProjectRepository;

import static akka.event.Logging.getLogger;
import static org.apache.commons.lang.StringUtils.isBlank;

public class ProjectCreatorActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final ProjectRepository projectRepository;

    private ProjectCreateMsg originalMsg;

    private ActorRef originalSender;

    public ProjectCreatorActor(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        receive(ReceiveBuilder
                .match(ProjectCreateMsg.class, this::onProjectCreate)
                .match(RightEndpointActor.RightRequestResultMsg.class, this::onRightResult)
                .matchAny(this::unhandled).build());
    }

    public static Props PROPS(ProjectRepository projectRepository) {
        if (projectRepository == null) {
            throw new IllegalArgumentException("projectRepository must be defined.");
        }
        return Props.create(ProjectCreatorActor.class, projectRepository);
    }

    private void onRightResult(RightEndpointActor.RightRequestResultMsg msg) {
        EventUserRequestMessage result = null;
        if (msg.isValid()) {
            String projectId = projectRepository.addProject(originalMsg.project, originalMsg.projectConfigurationIdentifier);
            ProjectBuilder builder = new ProjectBuilder(originalMsg.project);
            builder.setIdentifier(projectId);
            result = new ProjectCreateResultMsg(originalMsg.getRequester(), originalMsg.originalEvent(), builder.build());
        } else {
            result = new ProjectCreateNotAuthoriseMsg(originalMsg.getRequester(), originalMsg.originalEvent(), originalMsg.project);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User {} isn't authorised to update project {}.", originalMsg.getRequester().getUsername(), originalMsg.project.getName());
            }
        }
        originalSender.tell(result, self());
        getContext().stop(self());
    }

    private void onProjectCreate(ProjectCreateMsg msg) {
        originalMsg = msg;
        originalSender = sender();
        if (msg.getRequester() != null) {
            ProjectConfiguration projectConfiguration = projectRepository.getProjectConfigurationById(msg.project.getProjectConfigurationIdentifier());
            getContext().actorOf(RightEndpointActor.PROPS()).tell(new RightEndpointActor.UserAdminRightRequestMsg(msg.getRequester(), projectConfiguration), self());
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Add project '{}' from unknown requester.", msg.project.getName());
                LOGGER.debug("Add project {}", msg.project);
            }
            String projectId = projectRepository.addProject(originalMsg.project, originalMsg.projectConfigurationIdentifier);
            ProjectBuilder projectBuilder = new ProjectBuilder(originalMsg.project);
            projectBuilder.setIdentifier(projectId);
            originalSender.tell(new ProjectCreateResultMsg(msg.getRequester(), msg.originalEvent(), projectBuilder.build()), self());
            getContext().stop(self());
        }
    }

    public static class ProjectCreateMsg extends EventUserRequestMessage {

        private final Project project;

        private final String projectConfigurationIdentifier;

        private final boolean isEventBusOrigin;

        public ProjectCreateMsg(User requester, Event request, Project project, String projectConfigurationIdentifier , boolean isEventBusOrigin) {
            super(requester, request);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            if (isBlank(projectConfigurationIdentifier)) {
                throw new IllegalArgumentException("projectConfigurationIdentifier must be defined.");
            }
            this.project = project;
            this.projectConfigurationIdentifier = projectConfigurationIdentifier;
            this.isEventBusOrigin = isEventBusOrigin;
        }
        public ProjectCreateMsg(User requester, Event request, Project project, String projectConfigurationIdentifier) {
            this(requester, request, project, projectConfigurationIdentifier, false);
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return isEventBusOrigin;
        }
    }

    public static class ProjectCreateResultMsg extends EventUserReplyMessage {

        private final Project project;

        public ProjectCreateResultMsg(User requester,Event request, Project project) {
            super(requester, request,Event.PROJECT_CREATION_REPLY, project);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            this.project = project;
        }

        public Project getProject() {
            return project;
        }
    }

    public static class ProjectCreateNotAuthoriseMsg extends EventUserRequestMessage {

        private final Project project;

        public ProjectCreateNotAuthoriseMsg(User requester, Event request, Project project) {
            super(requester, request);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            this.project = project;
        }

        public Project getProject() {
            return project;
        }
    }

}
