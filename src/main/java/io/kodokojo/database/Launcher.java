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
package io.kodokojo.database;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.kodokojo.commons.config.MicroServiceConfig;
import io.kodokojo.commons.config.module.*;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.service.actor.EventToEndpointGateway;
import io.kodokojo.commons.service.repository.ProjectFetcher;
import io.kodokojo.database.config.module.AkkaModule;
import io.kodokojo.database.config.module.EmailModule;
import io.kodokojo.database.config.module.ZookeeperModule;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.commons.service.lifecycle.ApplicationLifeCycleManager;
import io.kodokojo.commons.service.repository.UserFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {


        Injector propertyInjector = Guice.createInjector(new CommonsPropertyModule(args));
        MicroServiceConfig microServiceConfig = propertyInjector.getInstance(MicroServiceConfig.class);
        LOGGER.info("Starting Kodo Kojo {}.", microServiceConfig.name());
        Injector servicesInjector = propertyInjector.createChildInjector(new UtilityServiceModule(), new EventBusModule(), new DatabaseModule(), new SecurityModule(), new EmailModule(), new ZookeeperModule());
        Injector akkaInjector = servicesInjector.createChildInjector(new AkkaModule());
        ActorSystem actorSystem = akkaInjector.getInstance(ActorSystem.class);
        ActorRef endpointActor = actorSystem.actorOf(EndpointActor.PROPS(akkaInjector), "endpoint");
        akkaInjector = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ActorRef.class).annotatedWith(Names.named(EndpointActor.NAME)).toInstance(endpointActor);
            }
        });
        Injector injector = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
            //
            }

            @Provides
            @Singleton
            EventToEndpointGateway provideEventEventToEndpointGateway(@Named(EndpointActor.NAME) ActorRef akkaEndpoint) {
                return new EventToEndpointGateway(akkaEndpoint);
            }

        });
        EventBus eventBus = injector.getInstance(EventBus.class);
        EventToEndpointGateway eventToActorGateway = injector.getInstance(EventToEndpointGateway.class);
        eventBus.addEventListener(eventToActorGateway);

        ApplicationLifeCycleManager applicationLifeCycleManager = servicesInjector.getInstance(ApplicationLifeCycleManager.class);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                super.run();
                LOGGER.info("Stopping services.");
                applicationLifeCycleManager.stop();
                LOGGER.info("All services stopped.");
            }
        });
        eventBus.connect();

        LOGGER.info("Kodo Kojo {} started.", microServiceConfig.name());

    }


}
