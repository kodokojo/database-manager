/**
 * Kodo Kojo - Microservice which allow to access to Database.
 * Copyright © 2017 Kodo Kojo (infos@kodokojo.io)
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
package io.kodokojo.database.service.actor.organisation;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.payload.OrganisationCreationReply;
import io.kodokojo.commons.model.Organisation;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.OrganisationRepository;
import io.kodokojo.database.service.actor.EndpointActor;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class OrganisationCreatorActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final OrganisationRepository organisationRepository;

    public OrganisationCreatorActor(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
        receive(ReceiveBuilder
                .match(OrganisationCreateMsg.class, this::onOrganisationCreateMsg)
                .matchAny(this::unhandled)
                .build());
    }

    public static Props PROPS(OrganisationRepository entityRepository) {
        requireNonNull(entityRepository, "organisationRepository must be defined.");
        return Props.create(OrganisationCreatorActor.class, entityRepository);
    }

    private void onOrganisationCreateMsg(OrganisationCreateMsg msg) {
        Organisation organisation = organisationRepository.getOrganisationByName(msg.organisation.getName());
        User requester = msg.getRequester();
        if (requester == null) {
            LOGGER.error("Trying to create organisation {} from unknown requester.", organisation.getName());
        } else {
            if (organisation == null) {
                String organisationId = organisationRepository.addOrganisation(msg.organisation);
                OrganisationMessage.ChangeUserToOrganisationMsg changeUserToOrganisationMsg = new OrganisationMessage.ChangeUserToOrganisationMsg(requester, OrganisationMessage.TypeChange.ADD, msg.originalEvent(), requester.getIdentifier(), organisationId, requester.isRoot());
                getContext().actorSelection(EndpointActor.ACTOR_PATH).tell(changeUserToOrganisationMsg, self());
                Future<Object> future = Patterns.ask(getContext().actorSelection(EndpointActor.ACTOR_PATH), changeUserToOrganisationMsg, 1000);
                try {
                    Await.result(future, Duration.apply(10, TimeUnit.SECONDS));
                    sender().tell(new OrganisationCreatedResultMsg(requester, msg.originalEvent(), organisationId, false), self());
                } catch (Exception e) {
                    LOGGER.error("Unable to add user {} to organisation {}.", requester.getUsername(), msg.organisation.getName());
                }
            } else {
                sender().tell(new OrganisationCreatedResultMsg(requester, msg.originalEvent(), organisation.getIdentifier(), true), self());
            }
        }
        getContext().stop(self());
    }

    public static class OrganisationCreateMsg extends EventUserRequestMessage {

        protected final Organisation organisation;

        private final boolean isEventBusOrigin;

        public OrganisationCreateMsg(User requester, Event request, Organisation organisation, boolean isEventBusOrigin) {
            super(requester, request);
            if (organisation == null) {
                throw new IllegalArgumentException("entity must be defined.");
            }
            this.organisation = organisation;
            this.isEventBusOrigin = isEventBusOrigin;
        }

        public OrganisationCreateMsg(User requester, Event request, Organisation organisation) {
            this(requester, request, organisation, false);
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return isEventBusOrigin;
        }
    }

    public class OrganisationCreatedResultMsg extends EventUserReplyMessage {

        private final String organisationId;

        private final boolean alreadyExist;

        public OrganisationCreatedResultMsg(User requester, Event request, String organisationId, boolean alreadyExist) {
            super(requester, request, Event.ORGANISATION_CREATE_REPLY, new OrganisationCreationReply(organisationId, alreadyExist));
            this.organisationId = organisationId;
            this.alreadyExist = alreadyExist;
        }

        public String getOrganisationId() {
            return organisationId;
        }

        public boolean isAlreadyExist() {
            return alreadyExist;
        }


    }

}
