/**
 * Kodo Kojo - Software factory done right
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
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.dto.BrickConfigDto;
import io.kodokojo.commons.dto.ProjectConfigurationCreationDto;
import io.kodokojo.commons.dto.StackConfigDto;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.*;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.database.service.actor.user.UserFetcherActor;
import io.kodokojo.database.service.actor.user.UserServiceCreatorActor;
import io.kodokojo.commons.service.BrickFactory;
import io.kodokojo.commons.service.DefaultBrickFactory;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static akka.event.Logging.getLogger;

public class ProjectConfigurationBuilderActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(BrickFactory brickFactory) {
        if (brickFactory == null) {
            throw new IllegalArgumentException("brickFactory must be defined.");
        }
        return Props.create(ProjectConfigurationBuilderActor.class, brickFactory);
    }

    private final BrickFactory brickFactory;

    private ActorRef originalSender;

    private ProjectConfigurationBuildMsg initialMsg;

    private UserService userService;

    private Set<User> admins;

    private Set<User> users;

    private int scmSshPort = 0;

    private List<StackConfigDto> stackConfigDtos;

    public ProjectConfigurationBuilderActor(BrickFactory brickFactory) {
        this.brickFactory = brickFactory;
        receive(ReceiveBuilder.match(ProjectConfigurationBuildMsg.class, msg -> {
            originalSender = sender();
            initialMsg = msg;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Receive a ProjectBuild request from Actor {}.", originalSender);
            }

            ProjectConfigurationCreationDto projectConfigurationCreationDto = msg.getProjectConfigurationCreationDto();

            Set<String> userRequested = new HashSet<>();
            userRequested.add(projectConfigurationCreationDto.getOwnerIdentifier());
            if (CollectionUtils.isNotEmpty(projectConfigurationCreationDto.getUserIdentifiers())) {
                userRequested.addAll(projectConfigurationCreationDto.getUserIdentifiers());
            }

            ActorSelection akkaEndpoint = getContext().actorSelection(EndpointActor.ACTOR_PATH);
            akkaEndpoint.tell(new UserFetcherActor.UserFetchMsg(msg.getRequester(), msg.originalEvent(), userRequested), self());
            akkaEndpoint.tell(new UserServiceCreatorActor.UserServiceCreateMsg(msg.getRequester(), msg.originalEvent(),msg.getProjectConfigurationCreationDto().getName() + "-service"), self());

            stackConfigDtos = CollectionUtils.isEmpty(projectConfigurationCreationDto.getStackConfigs()) ? new ArrayList<>() : projectConfigurationCreationDto.getStackConfigs();
            if (CollectionUtils.isEmpty(stackConfigDtos)) {
                List<BrickConfigDto> brickDtos = new ArrayList<>();
                addBrick(DefaultBrickFactory.JENKINS, brickDtos);
                addBrick(DefaultBrickFactory.GITLAB, brickDtos);
                addBrick(DefaultBrickFactory.NEXUS, brickDtos);
                StackConfigDto stackConfigDto = new StackConfigDto("build-A", StackType.BUILD.name(), brickDtos);
                stackConfigDtos.add(stackConfigDto);
            }

            StackConfigDto defaultStackConfigDtos = stackConfigDtos.get(0);
            BootstrapStackActor.BootstrapStackMsg bootstrapStackMsg = new BootstrapStackActor.BootstrapStackMsg(projectConfigurationCreationDto.getName(), defaultStackConfigDtos.getName(), StackType.valueOf(defaultStackConfigDtos.getType()));

            akkaEndpoint.tell(bootstrapStackMsg, self());
        }).match(UserFetcherActor.UserFetchResultMsg.class, msg -> {
            admins = msg.getUsers().stream()
                    .filter(filterByUserIds(Collections.singleton(initialMsg.getProjectConfigurationCreationDto().getOwnerIdentifier())))
                    .collect(Collectors.toSet());
            users = msg.getUsers().stream()
                    .filter(filterByUserIds(initialMsg.getProjectConfigurationCreationDto().getUserIdentifiers()))
                    .collect(Collectors.toSet());
            users.addAll(admins);
            tryToBuild();
        }).match(UserServiceCreatorActor.UserServiceCreateResultMsg.class, msg -> {
            userService = msg.getUserService();
            tryToBuild();
        })
                .match(BootstrapStackActor.BootstrapStackResultMsg.class, msg -> {

                    BootstrapStackData bootstrapStackData = msg.getBootstrapStackData();
                    scmSshPort = bootstrapStackData.getSshPort();
                    tryToBuild();
                })
                .matchAny(this::unhandled).build());
    }

    private static Predicate<User> filterByUserIds(Collection<String> userIds) {
        return user -> user != null && CollectionUtils.isNotEmpty(userIds) && userIds.contains(user.getIdentifier());
    }

    private void tryToBuild() {

        if (CollectionUtils.isNotEmpty(admins) &&
                scmSshPort > 0 &&
                userService != null) {

            User requester = initialMsg.getRequester();
            ProjectConfigurationCreationDto projectConfigurationCreationDto = initialMsg.getProjectConfigurationCreationDto();

            Set<StackConfiguration> stackConfiguration = stackConfigDtos.stream().map(stackConfigDto -> {
                Set<BrickConfiguration> brickConfigurations = stackConfigDto.getBrickConfigs().stream()
                        .map(brickConfigDto -> brickFactory.createBrick(brickConfigDto.getName()))
                        .collect(Collectors.toSet());
                return new StackConfiguration(stackConfigDto.getName(), StackType.valueOf(stackConfigDto.getType()), brickConfigurations, scmSshPort);
            }).collect(Collectors.toSet());


            ProjectConfiguration projectConfiguration = new ProjectConfiguration(requester.getEntityIdentifier(), projectConfigurationCreationDto.getName(), userService, new ArrayList<>(admins), stackConfiguration, new ArrayList<>(users));
            originalSender.tell(new ProjectConfigurationBuildResultMsg(initialMsg.getRequester(), initialMsg.originalEvent(),projectConfiguration), self());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Return a built ProjectConfiguration for project {} to actor {}.", initialMsg.getProjectConfigurationCreationDto().getName(), originalSender);
                LOGGER.debug("sshPort {}", scmSshPort);
            }
            getContext().stop(self());

        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Not yet ready to build Project configuration for project {}. scmSshPort={} userService created {} Get admins {}.", initialMsg.getProjectConfigurationCreationDto().getName(), scmSshPort, userService == null ? "NOT EXIST" : "DEFINED", CollectionUtils.isNotEmpty(admins) ? "EMPTY" : "DEFINED");
        }
    }

    private void addBrick(String name, List<BrickConfigDto> brickConfigDtos) {
        BrickConfiguration brickConfiguration = brickFactory.createBrick(name);
        brickConfigDtos.add(new BrickConfigDto(brickConfiguration.getName(), brickConfiguration.getType().toString(), brickConfiguration.getVersion()));
    }

    public static class ProjectConfigurationBuildMsg extends EventUserRequestMessage {

        private final ProjectConfigurationCreationDto projectConfigurationCreationDto;

        private final boolean comeFromEventBus;

        public ProjectConfigurationBuildMsg(User requester, Event request, ProjectConfigurationCreationDto projectConfigurationCreationDto) {
            this(requester, request, projectConfigurationCreationDto, false);
        }

        public ProjectConfigurationBuildMsg(User requester, Event request, ProjectConfigurationCreationDto projectConfigurationCreationDto, boolean comeFromEventBus) {
            super(requester, request);
            if (projectConfigurationCreationDto == null) {
                throw new IllegalArgumentException("projectCreationDto must be defined.");
            }
            this.projectConfigurationCreationDto = projectConfigurationCreationDto;
            this.comeFromEventBus = comeFromEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return comeFromEventBus;
        }

        public ProjectConfigurationCreationDto getProjectConfigurationCreationDto() {
            return projectConfigurationCreationDto;
        }
    }

    public static class ProjectConfigurationBuildResultMsg extends EventUserRequestMessage {

        private final ProjectConfiguration projectConfiguration;

        public ProjectConfigurationBuildResultMsg(User requester, Event request,ProjectConfiguration projectConfiguration) {
            super(requester, request);
            this.projectConfiguration = projectConfiguration;
        }

        public ProjectConfiguration getProjectConfiguration() {
            return projectConfiguration;
        }
    }

}
