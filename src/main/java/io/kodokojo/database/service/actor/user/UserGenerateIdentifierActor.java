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
package io.kodokojo.database.service.actor.user;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.actor.message.EventReplyableMessage;
import io.kodokojo.commons.service.repository.UserRepository;

import java.io.Serializable;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class UserGenerateIdentifierActor extends AbstractActor {

    public static Props PROPS(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("userRepository must be defined.");
        }
        return Props.create(UserGenerateIdentifierActor.class, userRepository);
    }

    public UserGenerateIdentifierActor(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("userRepository must be defined.");
        }

        receive(ReceiveBuilder.match(UserGenerateIdentifierMsg.class, msg -> {

            String generateId = userRepository.generateId();
            sender().tell(new UserGenerateIdentifierResultMsg(generateId, msg.originalEvent()), self());
            getContext().stop(self());

        }).matchAny(this::unhandled).build());
    }

    public static class UserGenerateIdentifierMsg implements EventBusOriginMessage {

        private final Event request;

        private final boolean comeFromEventBus;

        public UserGenerateIdentifierMsg(Event request) {
            this(request, false);
        }

        public UserGenerateIdentifierMsg(Event request, boolean comeFromEventBus) {
            requireNonNull(request, "request must be defined.");
            this.request = request;
            this.comeFromEventBus = comeFromEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return comeFromEventBus;
        }

        @Override
        public Event originalEvent() {
            return request;
        }
    }

    public static class UserGenerateIdentifierResultMsg implements EventReplyableMessage {

        private final String generateId;

        private final Event request;

        public UserGenerateIdentifierResultMsg(String generateId, Event request) {
            if (isBlank(generateId)) {
                throw new IllegalArgumentException("generateId must be defined.");
            }
            requireNonNull(request, "request must be defined.");
            this.generateId = generateId;
            this.request = request;
        }

        public String getGenerateId() {
            return generateId;
        }

        @Override
        public String eventType() {
            return Event.USER_IDENTIFIER_CREATION_REPLY;
        }

        @Override
        public Serializable payloadReply() {
            return generateId;
        }

        @Override
        public Event originalEvent() {
            return request;
        }
    }

}
