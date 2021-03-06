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

import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserReplyMessage;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;

import static java.util.Objects.requireNonNull;

public interface UserMessage {

    class UserUpdateMessageResultUser extends EventUserReplyMessage {

        private final boolean success;

        public UserUpdateMessageResultUser(User requester, Event request, boolean success) {
            super(requester , request, Event.USER_UPDATE_REPLY, success);
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    class UserUpdateMessageUser extends EventUserRequestMessage {

        private final User userToUpdate;

        private final String newPassword;

        private final String newSSHPublicKey;

        private final String email;

        private final String firstName;

        private final String lastName;

        private final boolean comeFromEventBus;

        public UserUpdateMessageUser(User requester, Event request, User userToUpdate, String newPassword, String newSSHPublicKey, String firstName, String lastName, String email) {
            this(requester, request, userToUpdate, newPassword, newSSHPublicKey, firstName, lastName, email, false);
        }
        public UserUpdateMessageUser(User requester, Event request, User userToUpdate, String newPassword, String newSSHPublicKey, String firstName, String lastName, String email, boolean comeFromEventBus) {
            super(requester, request);
            requireNonNull(userToUpdate, "userToUpdate must be defined.");
            this.userToUpdate = userToUpdate;
            this.newPassword = newPassword;
            this.newSSHPublicKey = newSSHPublicKey;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.comeFromEventBus = comeFromEventBus;
        }

        @Override
        public boolean initialSenderIsEventBus() {
            return comeFromEventBus;
        }

        public User getUserToUpdate() {
            return userToUpdate;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public String getNewSSHPublicKey() {
            return newSSHPublicKey;
        }

        public String getEmail() {
            return email;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }
    }
}
