/**
 * Kodo Kojo - Software factory done right
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
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.model.UserService;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;
import io.kodokojo.commons.service.repository.UserRepository;
import org.apache.commons.lang.StringUtils;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class UserServiceCreatorActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(UserRepository userRepository) {
        requireNonNull(userRepository, "userRepository must be defined.");
        return Props.create(UserServiceCreatorActor.class, userRepository);
    }

    private final UserRepository userRepository;

    private boolean isValid = false;

    private KeyPair keyPair;

    private String password = "";

    private UserServiceCreateMsg message;

    private ActorRef originalActor;

    public UserServiceCreatorActor(UserRepository userRepository) {
        requireNonNull(userRepository, "userRepository must be defined.");

        this.userRepository = userRepository;
        receive(ReceiveBuilder.match(UserServiceCreateMsg.class, u -> {
            originalActor = sender();
            message = u;
            getContext().actorOf(UserGenerateSecurityData.PROPS()).tell(new UserGenerateSecurityData.GenerateSecurityMsg(), self());
            getContext().actorOf(UserEligibleActor.PROPS(userRepository)).tell(new UserEligibleActor.UserEligibleMsg(u.username), self());

        })
                .match(UserEligibleActor.UserEligibleResultMsg.class, r -> {
                    isValid = r.isValid();
                    if (isValid) {
                        isReadyToStore();
                    } else {
                        if (!r.isIdExpected()) {
                            LOGGER.warning("User {} have not expected Id.", message.getUsername());
                            originalActor.tell(r, self());
                            getContext().stop(self());
                        }
                        LOGGER.warning("User service account {} not eligible.", message.getUsername());
                    }
                })
                .match(UserGenerateSecurityData.UserSecurityDataMsg.class, msg -> {
                    password = msg.getPassword();
                    keyPair = msg.getKeyPair();
                    isReadyToStore();
                })
                .build());
    }

    private void isReadyToStore() {
        if (isValid && keyPair != null && StringUtils.isNotBlank(password)) {
            String id = userRepository.generateId();
            UserService user = new UserService(id, message.username, message.username, password,(RSAPrivateKey) keyPair.getPrivate(),(RSAPublicKey) keyPair.getPublic());
            boolean added = userRepository.addUserService(user);
            if (added) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("User Service {} successfully created.", message.getUsername());
                }
                originalActor.tell(new UserServiceCreateResultMsg(message.getRequester(), message.originalEvent(), user, keyPair), self());
                getContext().stop(self());

            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Unable to store user service {}", user);
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Not yet ready to store the user service.");
        }
    }

    public static class UserServiceCreateMsg extends EventUserRequestMessage {

        protected final String username;

        public UserServiceCreateMsg(User requester, Event request, String username) {
            super(requester, request);

            if (isBlank(username)) {
                throw new IllegalArgumentException("username must be defined.");
            }
            this.username = username;
        }

        public String getUsername() {
            return username;
        }

        @Override
        public String toString() {
            return "UserServiceCreateMsg{" +
                    "username='" + username + '\'' +
                    '}';
        }
    }

    public static class UserServiceCreateResultMsg extends EventUserRequestMessage {

        private final UserService user;

        private final KeyPair keyPair;

        public UserServiceCreateResultMsg(User requester, Event request, UserService user, KeyPair keyPair) {
            super(requester, request);
            if (user == null) {
                throw new IllegalArgumentException("user must be defined.");
            }
            if (keyPair == null) {
                throw new IllegalArgumentException("keyPair must be defined.");
            }
            this.user = user;
            this.keyPair = keyPair;
        }

        public UserService getUserService() {
            return user;
        }

        public KeyPair getKeyPair() {
            return keyPair;
        }
    }

}
