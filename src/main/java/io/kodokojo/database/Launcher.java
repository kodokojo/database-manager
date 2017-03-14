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
package io.kodokojo.database;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.inject.*;
import com.google.inject.name.Names;
import io.kodokojo.commons.RSAUtils;
import io.kodokojo.commons.config.MicroServiceConfig;
import io.kodokojo.commons.config.module.*;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.model.Organisation;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.EventToEndpointGateway;
import io.kodokojo.commons.service.healthcheck.HttpHealthCheckEndpoint;
import io.kodokojo.commons.service.repository.OrganisationRepository;
import io.kodokojo.commons.service.repository.Repository;
import io.kodokojo.commons.service.repository.UserRepository;
import io.kodokojo.database.config.DatabaseConfig;
import io.kodokojo.database.config.module.*;
import io.kodokojo.database.service.actor.EndpointActor;
import io.kodokojo.commons.service.lifecycle.ApplicationLifeCycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {

        Injector propertyInjector = Guice.createInjector(new CommonsPropertyModule(args), new PropertyModule());
        DatabaseConfig databaseConfig = propertyInjector.getInstance(DatabaseConfig.class);
        AbstractModule configurationStoreModule = new ZookeeperModule();
        if (databaseConfig.configurationStoreSelector().equals("noop")) {
            configurationStoreModule = new NoopCongurationStoreModule();
        }

        MicroServiceConfig microServiceConfig = propertyInjector.getInstance(MicroServiceConfig.class);
        LOGGER.info("Starting Kodo Kojo {}.", microServiceConfig.name());
        Injector servicesInjector = propertyInjector.createChildInjector(
                new UtilityServiceModule(),
                new EventBusModule(),
                new DatabaseModule(),
                new SecurityModule(),
                new EmailModule(),
                configurationStoreModule,
                new CommonsHealthCheckModule()
        );
        Injector akkaInjector = servicesInjector.createChildInjector(new AkkaModule());
        ActorSystem actorSystem = akkaInjector.getInstance(ActorSystem.class);
        ActorRef endpointActor = actorSystem.actorOf(EndpointActor.PROPS(akkaInjector), "endpoint");
        akkaInjector = akkaInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ActorRef.class).annotatedWith(Names.named(EndpointActor.NAME)).toInstance(endpointActor);
            }
        });

        Injector injector = akkaInjector.createChildInjector(new AkkaGatewayModule());

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

        Repository repository = injector.getInstance(Repository.class);
        User root = repository.getUserByUsername(databaseConfig.rootUsername());
        if (root == null) {
            String organisationId = repository.addOrganisation(new Organisation(databaseConfig.rootUsername()));
            String id = repository.generateId();

            repository.addUser(new User(id, Collections.singleton(organisationId), databaseConfig.rootUsername(), databaseConfig.rootUsername(), databaseConfig.rootUsername(), databaseConfig.rootUsername() + "@kodokojo.io", databaseConfig.rootPassword(),"",true));
            repository.addAdminToOrganisation(id, organisationId);
            LOGGER.info("Create first root user with username {}.", databaseConfig.rootUsername());
        } else {
            LOGGER.debug("User root already exist: {}", root.toString());
        }

        eventBus.connect();

        HttpHealthCheckEndpoint httpHealthCheckEndpoint = injector.getInstance(HttpHealthCheckEndpoint.class);
        httpHealthCheckEndpoint.start();

        LOGGER.info("Kodo Kojo {} started.", microServiceConfig.name());

    }

}
