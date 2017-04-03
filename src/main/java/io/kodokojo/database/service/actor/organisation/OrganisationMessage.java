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
package io.kodokojo.database.service.actor.organisation;

import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.payload.OrganisationChangeUserRequest;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventUserRequestMessage;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public interface OrganisationMessage {

    enum TypeChange {
        ADD,
        REMOVE
    }

    class ChangeUserToOrganisationMsg extends EventUserRequestMessage {

        protected final TypeChange typeChange;

        protected final String userId;

        protected final String organisationId;

        protected final boolean admin;

        public ChangeUserToOrganisationMsg(User requester, TypeChange typeChange, Event request, String userId, String organisationId, boolean admin) {
            super(requester, request);
            if (isBlank(userId)) {
                throw new IllegalArgumentException("userId must be defined.");
            }
            if (isBlank(organisationId)) {
                throw new IllegalArgumentException("entityId must be defined.");
            }
            requireNonNull(typeChange, "typeChange must be defined.");
            this.typeChange = typeChange;
            this.userId = userId;
            this.organisationId = organisationId;
            this.admin = admin;
        }
    }
}
