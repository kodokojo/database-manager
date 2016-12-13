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
package io.kodokojo.database.config.module;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.DeadLetter;
import akka.actor.Props;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import io.kodokojo.commons.config.ApplicationConfig;
import io.kodokojo.database.service.BootstrapConfigurationProvider;
import io.kodokojo.database.service.ConfigurationStore;
import io.kodokojo.database.service.actor.entity.EntityEndpointActor;
import io.kodokojo.database.service.actor.project.ProjectEndpointActor;
import io.kodokojo.database.service.actor.user.UserEndpointActor;
import io.kodokojo.commons.service.BrickFactory;
import io.kodokojo.commons.service.actor.DeadLetterActor;
import io.kodokojo.commons.service.repository.EntityRepository;
import io.kodokojo.commons.service.repository.ProjectRepository;
import io.kodokojo.commons.service.repository.UserRepository;


public class AkkaModule extends AbstractModule {

    @Override
    protected void configure() {
        ActorSystem actorSystem = ActorSystem.apply("kodokojo");
        ActorRef deadletterlistener = actorSystem.actorOf(DeadLetterActor.PROPS(), "deadletterlistener");
        actorSystem.eventStream().subscribe(deadletterlistener, DeadLetter.class);
        bind(ActorSystem.class).toInstance(actorSystem);
    }

    @Provides
    @Named(UserEndpointActor.NAME)
    Props provideUserEndpointProps(UserRepository userRepository, ApplicationConfig applicationConfig) {
        return UserEndpointActor.PROPS(userRepository, applicationConfig);
    }

    @Provides
    @Named(ProjectEndpointActor.NAME)
    Props provideProjectEndpointProps(ProjectRepository projectRepository, BrickFactory brickFactory, BootstrapConfigurationProvider bootstrapConfigurationProvider, ConfigurationStore configurationStore) {
        return ProjectEndpointActor.PROPS(projectRepository, brickFactory, bootstrapConfigurationProvider, configurationStore);
    }

    @Provides
    @Named(EntityEndpointActor.NAME)
    Props provideEntityEndpointProps(EntityRepository entityRepository) {
        return EntityEndpointActor.PROPS(entityRepository);
    }

}
