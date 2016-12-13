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
package io.kodokojo.database.service.actor.entity;

import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;

import static org.apache.commons.lang.StringUtils.isBlank;

public interface EntityMessage {
    class AddUserToEntityMsg extends EventUserRequestMessage {

        protected final String userId;

        protected final String entityId;

        public AddUserToEntityMsg(User requester, Event request, String userId, String entityId) {
            super(requester, request);
            if (isBlank(userId)) {
                throw new IllegalArgumentException("userId must be defined.");
            }
            if (isBlank(entityId)) {
                throw new IllegalArgumentException("entityId must be defined.");
            }
            this.userId = userId;
            this.entityId = entityId;
        }
    }
}
