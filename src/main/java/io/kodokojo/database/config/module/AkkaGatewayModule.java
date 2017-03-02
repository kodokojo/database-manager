package io.kodokojo.database.config.module;

import akka.actor.ActorRef;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.kodokojo.commons.service.actor.EventToEndpointGateway;
import io.kodokojo.database.service.actor.EndpointActor;

public class AkkaGatewayModule extends AbstractModule {

        @Override
        protected void configure() {
            //
        }

        @Provides
        @Singleton
        EventToEndpointGateway provideEventEventToEndpointGateway(@Named(EndpointActor.NAME) ActorRef akkaEndpoint) {
            return new EventToEndpointGateway(akkaEndpoint);
        }


}
