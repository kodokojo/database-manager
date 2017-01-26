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
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.*;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.commons.service.actor.message.BrickStateEvent;
import io.kodokojo.commons.service.repository.ProjectRepository;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class BrickStateEventPersistenceActor extends AbstractActor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrickStateEventPersistenceActor.class);

    public static Props PROPS(ProjectRepository projectRepository) {
        requireNonNull(projectRepository, "projectRepository must be defined.");
        return Props.create(BrickStateEventPersistenceActor.class, projectRepository);
    }

    private final ProjectRepository projectRepository;

    private ActorRef originalSender;

    public BrickStateEventPersistenceActor(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        LOGGER.debug("Create a new BrickStateEventPersistenceActor.");
        receive(ReceiveBuilder.match(BrickStateEventPersistenceMsg.class, this::onBrickStateChange)
                .match(ProjectUpdaterMessages.ProjectUpdateResultMsg.class, msg -> onProjectUdapted())
                .match(ProjectUpdaterMessages.ProjectUpdateNotAuthoriseMsg.class, msg -> onNotAuthorizedToUpdateProject())
                .matchAny(this::unhandled).build());
    }

    private void onBrickStateChange(BrickStateEventPersistenceMsg msg) {
        BrickStateEvent brickStateEvent = msg.originalEvent().getPayload(BrickStateEvent.class);
        LOGGER.debug("Receive BrickStateEvent for project configuration identifier {}.", brickStateEvent.getProjectConfigurationIdentifier());
        originalSender = sender();
        Project project = projectRepository.getProjectByProjectConfigurationId(brickStateEvent.getProjectConfigurationIdentifier());
        if (project == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Unable to find project configuration id '{}'.", brickStateEvent.getProjectConfigurationIdentifier());
            }
            getContext().stop(self());
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Following project may be updated: {}", project);
        }

        ProjectBuilder builder = new ProjectBuilder(project).setSnapshotDate(new Date());
        Stack stack = findOrCreateStack(project, brickStateEvent.getStackName());
        Set<Stack> stacks = new HashSet<>(project.getStacks());
        Set<BrickStateEvent> brickStateEvents = stack.getBrickStateEvents();
        Optional<BrickStateEvent> initialBrickStateEvent = brickStateEvents.stream()
                .filter(b -> b.getBrickName().equals(brickStateEvent.getBrickName()) &&
                        b.getBrickType().equals(brickStateEvent.getBrickType()))
                .findFirst();
        String actionLog = "Adding";
        if (initialBrickStateEvent.isPresent()) {
            actionLog = "Updating";
            brickStateEvents.remove(initialBrickStateEvent.get());
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} following state to project {} : {}", actionLog, project.getName(), msg);
        }
        brickStateEvents.add(brickStateEvent);
        stacks.add(stack);

        builder.setStacks(stacks);
        getContext().actorFor(EndpointActor.ACTOR_PATH).tell(new ProjectUpdaterMessages.ProjectUpdateMsg(null,null, builder.build()), self());
    }

    private void onNotAuthorizedToUpdateProject() {
        LOGGER.error("Unexpected behavior happened when trying to update a project state from an brick state change notification.");
        getContext().stop(self());
    }

    private void onProjectUdapted() {
        originalSender.tell(Futures.successful(Boolean.TRUE), self());
        getContext().stop(self());
    }

    protected static Stack findOrCreateStack(Project project, String stackName) {
        assert project != null : "project must be defined.";
        assert StringUtils.isNotBlank(stackName) : "stackName must be defined.";
        Optional<Stack> stack = project.getStacks().stream().filter(s -> s.getName().equals(stackName)).findFirst();
        if (stack.isPresent()) {
            return stack.get();
        }
        return new Stack(stackName, StackType.BUILD, new HashSet<>());
    }

    public static class BrickStateEventPersistenceMsg extends EventUserRequestMessage {

        public BrickStateEventPersistenceMsg(User requester, Event request) {
            super(requester, request);
        }
    }

}
