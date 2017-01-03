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
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.UserFetcher;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class UserFetcherActor extends AbstractActor {

    public static Props PROPS(UserFetcher userFetcher) {
        if (userFetcher == null) {
            throw new IllegalArgumentException("userFetcher must be defined.");
        }
        return Props.create(UserFetcherActor.class, userFetcher);
    }

    public UserFetcherActor(UserFetcher userFetcher) {
        receive(ReceiveBuilder
                .match(UserFetchMsg.class, msg -> {
                    Set<User> users = msg.userIds.stream()
                            .map(userFetcher::getUserByIdentifier)
                            .collect(Collectors.toSet());
                    sender().tell(new UserFetchResultMsg(msg.getRequester(), msg.originalEvent(), msg.userIds, users), self());
                    getContext().stop(self());
                })
                .matchAny(this::unhandled).build());
    }

    public static class UserFetchMsg extends EventUserRequestMessage {

        private final Set<String> userIds;

        public UserFetchMsg(User requester, Event request, Set<String> userIds) {
            super(requester, request);
            this.userIds = userIds;
        }

        public UserFetchMsg(User requester, Event request, String userId) {
            this(requester, request, Collections.singleton(userId));
        }
    }

    public static class UserFetchResultMsg extends EventUserRequestMessage {

        private final Set<String> userIdRequeted;

        private final Set<User> users;

        public UserFetchResultMsg(User requester, Event request, Set<String> userIdRequeted, Set<User> users) {
            super(requester, request);
            this.userIdRequeted = userIdRequeted;
            this.users = users;
        }

        public Set<String> getUserIdRequeted() {
            return userIdRequeted;
        }

        public Set<User> getUsers() {
            return users;
        }
    }

}
