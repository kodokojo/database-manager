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
package io.kodokojo.database.service.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.japi.pf.UnitPFBuilder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.kodokojo.commons.dto.ProjectConfigurationCreationDto;
import io.kodokojo.commons.dto.UserUpdateDto;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.EventBuilder;
import io.kodokojo.commons.event.EventBuilderFactory;
import io.kodokojo.commons.event.payload.ProjectConfigurationChangeUserRequest;
import io.kodokojo.commons.event.payload.ProjectConfigurationCreated;
import io.kodokojo.commons.event.payload.UserCreated;
import io.kodokojo.commons.event.payload.UserCreationRequest;
import io.kodokojo.commons.model.*;
import io.kodokojo.commons.service.EmailSender;
import io.kodokojo.commons.service.actor.AbstractEventEndpointActor;
import io.kodokojo.commons.service.actor.EmailSenderActor;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.actor.message.EventReplyableMessage;
import io.kodokojo.database.service.actor.organisation.OrganisationCreatorActor;
import io.kodokojo.database.service.actor.organisation.OrganisationEndpointActor;
import io.kodokojo.database.service.actor.organisation.OrganisationMessage;
import io.kodokojo.database.service.actor.project.*;
import io.kodokojo.database.service.actor.user.*;
import javaslang.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class EndpointActor extends AbstractEventEndpointActor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointActor.class);

    private final ActorRef userEndpoint;

    private final ActorRef organisationEndpoint;

    private final ActorRef projectEndpoint;

    public static Props PROPS(Injector injector) {
        requireNonNull(injector, "injector must be defined.");
        return Props.create(EndpointActor.class, injector);
    }

    public EndpointActor(Injector injector) {
        super(injector);

        userEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(UserEndpointActor.NAME))), "userEndpoint");
        organisationEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(OrganisationEndpointActor.NAME))), "organisationEndpoint");
        projectEndpoint = getContext().actorOf(injector.getInstance(Key.get(Props.class, Names.named(ProjectEndpointActor.NAME))), "projectEndpoint");
    }

    @Override
    protected Try<ActorRefWithMessage> convertToActorRefWithMessage(Event event, User requester) {
        EventBusOriginMessage msg = null;
        ActorRef actorRef = null;

        switch (event.getEventType()) {
            case Event.USER_IDENTIFIER_CREATION_REQUEST:
                msg = new UserGenerateIdentifierActor.UserGenerateIdentifierMsg(event, true);
                actorRef = userEndpoint;
                break;
            case Event.USER_CREATION_REQUEST:
                UserCreationRequest creationRequest = event.getPayload(UserCreationRequest.class);
                msg = new UserCreatorActor.EventUserCreateMsg(requester, event, creationRequest.getId(), creationRequest.getEmail(), creationRequest.getUsername(), creationRequest.getOrganisationId(), creationRequest.isRoot());
                actorRef = userEndpoint;
                break;
            case Event.USER_UPDATE_REQUEST:
                UserUpdateDto userDto = event.getPayload(UserUpdateDto.class);
                User userToUpdate = userFetcher.getUserByIdentifier(userDto.getIdentifier());
                msg = new UserMessage.UserUpdateMessageUser(requester, event, userToUpdate, userDto.getPassword(), userDto.getSshPublicKey(), userDto.getFirstName(), userDto.getLastName(), userDto.getEmail(), true);
                actorRef = userEndpoint;
                break;
            case Event.PROJECTCONFIG_CREATION_REQUEST:
                ProjectConfigurationCreationDto projectConfigurationCreationDto = event.getPayload(ProjectConfigurationCreationDto.class);
                msg = new ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateMsg(requester, event, projectConfigurationCreationDto, true);
                actorRef = projectEndpoint;
                break;

            case Event.PROJECT_CREATION_REQUEST:
                Project project = event.getPayload(Project.class);
                msg = new ProjectCreatorActor.ProjectCreateMsg(requester, event, project, project.getProjectConfigurationIdentifier(), true);
                actorRef = projectEndpoint;
                break;
            case Event.ORGANISATION_CREATE_REQUEST:
                String name = event.getPayload(String.class);
                Organisation organisation = new Organisation(name);
                msg = new OrganisationCreatorActor.OrganisationCreateMsg(requester, event, organisation);
                break;
            case Event.BRICK_STATE_UPDATE:
                msg = new BrickStateEventPersistenceActor.BrickStateEventPersistenceMsg(requester, event);
                actorRef = projectEndpoint;
                break;

            case Event.PROJECTCONFIG_CHANGE_USER_REQUEST:
                ProjectConfigurationChangeUserRequest changeUserRequest = event.getPayload(ProjectConfigurationChangeUserRequest.class);
                msg = new ProjectConfigurationChangeUserActor.ProjectConfigurationChangeEventUserMsg(requester, event, TypeChange.valueOf(changeUserRequest.getTypeChange().toString()), changeUserRequest.getProjectConfigurationId(), changeUserRequest.getUserIdentifiers());
                actorRef = projectEndpoint;
                break;

            case Event.BRICK_PROPERTY_UPDATE_REQUEST:
                BrickConfigurerData brickConfigurerData = event.getPayload(BrickConfigurerData.class);
                msg = new BrickPropertyToBrickConfigurationActor.BrickPropertyToBrickConfigurationMsg(
                        requester,
                        event,
                        brickConfigurerData.getProjectConfigurationIdentifier(),
                        brickConfigurerData.getStackName(),
                        brickConfigurerData.getBrickName(),
                        brickConfigurerData.getContext()
                );
                break;

            default:
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Not recognize event type '{}' from following event, drop it : {}", event.getEventType(), Event.convertToJson(event));
                }
                break;
        }
        final EventBusOriginMessage finalMsg = msg;
        final ActorRef finalActorRef = actorRef;
        return Try.of(() -> new ActorRefWithMessage(finalActorRef, finalMsg));
    }


    @Override
    protected UnitPFBuilder<Object> messageMatcherBuilder() {
        return ReceiveBuilder.match(UserGenerateIdentifierActor.UserGenerateIdentifierMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserCreatorActor.EventUserCreateMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserFetcherActor.UserFetchMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(UserServiceCreatorActor.UserServiceCreateMsg.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(OrganisationCreatorActor.OrganisationCreateMsg.class, msg -> {
            dispatch(msg, sender(), organisationEndpoint);
        }).match(OrganisationMessage.AddUserToOrganisationMsg.class, msg -> {
            dispatch(msg, sender(), organisationEndpoint);
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
        }).match(UserMessage.UserUpdateMessageUser.class, msg -> {
            dispatch(msg, sender(), userEndpoint);
        }).match(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(BootstrapStackActor.BootstrapStackMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(BrickPropertyToBrickConfigurationActor.BrickPropertyToBrickConfigurationMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(ProjectUpdaterMessages.ListAndUpdateUserToProjectMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        }).match(BrickUpdateUserMsg.class, msg -> {
            dispatch(msg, sender(), projectEndpoint);
        });

    }

    @Override
    protected void onEventReplyableMessagePostReply(EventReplyableMessage msg, EventBuilderFactory eventBuilderFactory) {
        if (msg.originalEvent() != null) {
            if (msg instanceof UserCreatorActor.UserCreateResultMsg) {
                UserCreatorActor.UserCreateResultMsg createResultMsg = (UserCreatorActor.UserCreateResultMsg) msg;
                User user = createResultMsg.getUser();
                EventBuilder eventBuilder = eventBuilderFactory.create()
                        .setEventType(Event.USER_CREATION_EVENT)
                        .copyCustomHeader(msg.originalEvent(), Event.REQUESTER_ID_CUSTOM_HEADER)
                        .addCustomHeader(Event.ORGANISATION_ID_CUSTOM_HEADER, user.getOrganisationIds().iterator().next())
                        .setPayload(new UserCreated(user.getIdentifier(), user.getUsername(), user.getEmail()));

                eventBus.send(eventBuilder.build());
            } else if (msg instanceof ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateResultMsg) {
                ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateResultMsg createResultMsg = (ProjectConfigurationDtoCreatorActor.ProjectConfigurationDtoCreateResultMsg) msg;
                EventBuilder eventBuilder = eventBuilderFactory.create();
                User user = createResultMsg.getRequester();
                if (user != null) {
                    eventBuilder.addCustomHeader(Event.ORGANISATION_ID_CUSTOM_HEADER, createResultMsg.getOrganisationId());
                }
                eventBuilder.setEventType(Event.PROJECTCONFIG_CREATION_EVENT)
                        .copyCustomHeader(msg.originalEvent(), Event.REQUESTER_ID_CUSTOM_HEADER)
                        .setPayload(new ProjectConfigurationCreated(createResultMsg.getProjectConfigurationId(), createResultMsg.getProjectName()));
                eventBus.send(eventBuilder.build());
            } else if (msg instanceof OrganisationCreatorActor.OrganisationCreatedResultMsg) {
                OrganisationCreatorActor.OrganisationCreatedResultMsg organisationCreatedResultMsg = (OrganisationCreatorActor.OrganisationCreatedResultMsg) msg;
                EventBuilder eventBuilder = eventBuilderFactory.create();
                String organisationId = organisationCreatedResultMsg.getOrganisationId();
                eventBuilder
                        .setEventType(Event.ORGANISATION_CREATED)
                        .addCustomHeader(Event.ORGANISATION_ID_CUSTOM_HEADER, organisationId)
                        .setPayload(organisationId);
                eventBus.send(eventBuilder.build());
            }
        }
    }

}
