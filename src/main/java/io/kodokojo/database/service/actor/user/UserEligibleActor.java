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
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.service.repository.UserRepository;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Defined if a given user id is valid for creation, and check if username respect the db policy.
 */
public class UserEligibleActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(UserRepository userRepository) {
        requireNonNull(userRepository, "userRepository must be defined.");
        return Props.create(UserEligibleActor.class, userRepository);
    }

    public UserEligibleActor(UserRepository userRepository) {
        receive(ReceiveBuilder.match(UserCreatorActor.EventUserCreateMsg.class, msg -> {
            String id = msg.id;
            String username = msg.username;
            boolean identifierExpected = userRepository.identifierExpectedNewUser(id);
            boolean alreadyExist = false;
            if (identifierExpected) {
                alreadyExist = userRepository.getUserByUsername(username) != null;
            }
            UserEligibleResultMsg resultMsg = new UserEligibleResultMsg(alreadyExist, identifierExpected, true);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User {} is {}eligible to be insert in database.", msg.getUsername(), resultMsg.isValid() ? "" : "NOT ");
            }
            sender().tell(resultMsg, self());
            getContext().stop(self());
        }).match(UserEligibleMsg.class, msg -> {
            boolean res = userRepository.getUserServiceByName(msg.username) == null;
            sender().tell(new UserEligibleResultMsg(res, true, true), self());
            getContext().stop(self());
        })
                .matchAny(this::unhandled).build());

    }

    public static class UserEligibleMsg {
        private final String username;

        public UserEligibleMsg(String username) {
            if (isBlank(username)) {
                throw new IllegalArgumentException("username must be defined.");
            }
            this.username = username;
        }
    }

    public static class UserEligibleResultMsg {

        private final boolean alreadyExist;

        private final boolean idExpected;

        private final boolean userNameValid;

        public UserEligibleResultMsg(boolean alreadyExist, boolean idExpected, boolean userNameValid) {
            this.alreadyExist = alreadyExist;
            this.idExpected = idExpected;
            this.userNameValid = userNameValid;
        }

        public boolean isValid() {
            return idExpected && userNameValid;
        }

        public boolean isAlreadyExist() {
            return alreadyExist;
        }

        public boolean isIdExpected() {
            return idExpected;
        }

        public boolean isUserNameValid() {
            return userNameValid;
        }
    }

}
