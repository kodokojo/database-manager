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
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.service.BrickFactory;
import io.kodokojo.commons.service.repository.ProjectRepository;
import io.kodokojo.database.service.BootstrapConfigurationProvider;
import io.kodokojo.database.service.ConfigurationStore;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class ProjectEndpointActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(ProjectRepository projectRepository,
                              BrickFactory brickFactory,
                              BootstrapConfigurationProvider bootstrapConfigurationProvider,
                              ConfigurationStore configurationStore) {
        requireNonNull(projectRepository, "projectRepository must be defined.");
        requireNonNull(brickFactory, "brickFactory must be defined.");
        requireNonNull(configurationStore, "configurationStore must be defined.");
        requireNonNull(bootstrapConfigurationProvider, "bootstrapConfigurationProvider must be defined.");

        return Props.create(ProjectEndpointActor.class, projectRepository, brickFactory, bootstrapConfigurationProvider, configurationStore);
    }

    public static final String NAME = "projectEndpointProps";

    public ProjectEndpointActor(ProjectRepository projectRepository,
                                BrickFactory brickFactory,
                                BootstrapConfigurationProvider bootstrapConfigurationProvider,
                                ConfigurationStore configurationStore) {

        receive(ReceiveBuilder
                .match(ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg.class, msg -> {
                    LOGGER.debug("Forward building of ProjectConfiguration to ProjectConfigurationBuilderActor.");
                    getContext().actorOf(ProjectConfigurationBuilderActor.PROPS(brickFactory)).forward(msg, getContext());

                })
                .match(ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateMsg.class, msg -> {
                    getContext().actorOf(ProjectConfigurationDtoCreatorActor.PROPS(projectRepository)).forward(msg, getContext());
                })
                .match(ProjectUpdaterMessages.ProjectUpdateMsg.class, msg -> {
                    getContext().actorOf(ProjectUpdaterActor.props(projectRepository)).forward(msg, getContext());
                })
                .match(ProjectCreatorActor.ProjectCreateMsg.class, msg -> {
                    getContext().actorOf(ProjectCreatorActor.PROPS(projectRepository)).forward(msg, getContext());
                }).match(ProjectCreatorActor.ProjectCreateMsg.class, msg -> {
                    getContext().actorOf(ProjectCreatorActor.PROPS(projectRepository)).forward(msg, getContext());
                }).match(ProjectConfigurationUpdaterActor.ProjectConfigurationUpdaterMsg.class, msg -> {
                    getContext().actorOf(ProjectConfigurationUpdaterActor.PROPS(projectRepository)).forward(msg, getContext());
                }).match(BootstrapStackActor.BootstrapStackMsg.class, msg -> {
                    getContext().actorOf(BootstrapStackActor.PROPS(bootstrapConfigurationProvider, configurationStore)).forward(msg, getContext());
                }).match(ProjectConfigurationChangeUserActor.ProjectConfigurationChangeEventUserMsg.class, msg -> {
                    getContext().actorOf(ProjectConfigurationChangeUserActor.PROPS(projectRepository)).forward(msg, getContext());
                })
                .matchAny(this::unhandled).build());
    }


}
