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
package io.kodokojo.database.service.actor.user;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.model.UserService;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.UserFetcher;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class UserServiceFetcherActor extends AbstractActor {

    public static Props PROPS(UserFetcher userFetcher) {
        requireNonNull(userFetcher, "userFetcher must be defined.");
        return Props.create(UserServiceFetcherActor.class, userFetcher);
    }

    public UserServiceFetcherActor(UserFetcher userFetcher) {
        receive(ReceiveBuilder
                .match(UserServiceFetchMsg.class, msg -> {
                    Set<UserService> users = msg.usernames.stream()
                            .map(userFetcher::getUserServiceByName)
                            .collect(Collectors.toSet());
                    sender().tell(new UserServiceFetchResultMsg(msg.getRequester(), msg.originalEvent(), msg.usernames, users), self());
                    getContext().stop(self());
                })
                .matchAny(this::unhandled).build());
    }

    public static class UserServiceFetchMsg extends EventUserRequestMessage {

        private final Set<String> usernames;

        public UserServiceFetchMsg(User requester, Event request, Set<String> usernames) {
            super(requester, request);
            this.usernames = usernames;
        }

        public UserServiceFetchMsg(User requester, Event request, String username) {
            this(requester, request, Collections.singleton(username));
        }
    }

    public static class UserServiceFetchResultMsg extends EventUserRequestMessage {

        private final Set<String> userIdRequeted;

        private final Set<UserService> users;

        public UserServiceFetchResultMsg(User requester, Event request, Set<String> userIdRequeted, Set<UserService> users) {
            super(requester, request);
            this.userIdRequeted = userIdRequeted;
            this.users = users;
        }

        public Set<String> getUserIdRequeted() {
            return userIdRequeted;
        }

        public Set<UserService> getUsers() {
            return users;
        }
    }

}
