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
package io.kodokojo.database.service.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.japi.pf.UnitPFBuilder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.EventBuilder;
import io.kodokojo.commons.event.EventBuilderFactory;
import io.kodokojo.commons.event.EventBus;
import io.kodokojo.commons.service.actor.AbstractEndpointActor;
import io.kodokojo.database.service.actor.entity.EntityCreatorActor;
import io.kodokojo.database.service.actor.entity.EntityEndpointActor;
import io.kodokojo.database.service.actor.entity.EntityMessage;
import io.kodokojo.database.service.actor.project.*;
import io.kodokojo.database.service.actor.user.*;
import io.kodokojo.commons.service.EmailSender;
import io.kodokojo.commons.service.actor.EmailSenderActor;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.actor.message.EventReplyableMessage;
import org.apache.commons.lang.StringUtils;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import static akka.event.Logging.getLogger;

public class EndpointActor extends AbstractEndpointActor {

    public static final String ACTOR_PATH = "/user/endpoint";

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(Injector injector) {
        if (injector == null) {
            throw new IllegalArgumentException("injector must be defined.");
        }
        return Props.create(EndpointActor.class, injector);
    }

    public static final String NAME = "endpointAkka";

    private final ActorRef userEndpoint;

    private final ActorRef entityEndpoint;

    private final ActorRef projectEndpoint;

    public EndpointActor(Injector injector) {
        super(injector);
        userEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(UserEndpointActor.NAME))), "userEndpoint");
        entityEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(EntityEndpointActor.NAME))), "entityEndpoint");
        projectEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(ProjectEndpointActor.NAME))), "projectEndpoint");

    }

    @Override
    protected UnitPFBuilder<Object> messageMatcherBuilder() {
        return ReceiveBuilder.match(UserGenerateIdentifierActor.UserGenerateIdentifierMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserCreatorActor.EventCreateMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserFetcherActor.UserFetchMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserServiceCreatorActor.UserServiceCreateMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(EntityCreatorActor.EntityCreateMsg.class, msg -> {
            dispatch(msg, sender(), entityEndpoint);
        }).match(EntityMessage.AddUserToEntityMsg.class, msg -> {
            dispatch(msg, sender(), entityEndpoint);
        }).match(ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectCreatorActor.ProjectCreateMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectUpdaterMessages.ProjectUpdateMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectConfigurationUpdaterActor.ProjectConfigurationUpdaterMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(BrickPropertyToBrickConfigurationActor.BrickPropertyToBrickConfigurationMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(EmailSenderActor.EmailSenderMsg.class, msg -> {
            ActorRef emailSenderActor = getContext().actorOf(EmailSenderActor.PROPS(injector.getInstance(EmailSender.class)));
            dispatch(msg, sender(), emailSenderActor);
        }).match(UserMessage.UserUpdateMessage.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        });

    }

}
