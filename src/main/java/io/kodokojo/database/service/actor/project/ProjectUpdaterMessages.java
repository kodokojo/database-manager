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
package io.kodokojo.database.service.actor.project;

import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.model.Project;
import io.kodokojo.commons.model.UpdateData;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.message.EventRequestMessage;

import static java.util.Objects.requireNonNull;

public interface ProjectUpdaterMessages {

    class ProjectUpdateMsg extends EventRequestMessage {

        final Project project;

        public ProjectUpdateMsg(User requester, Event request, Project project) {
            super(requester, request);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            this.project = project;
        }
    }

    class ProjectUpdateResultMsg extends EventRequestMessage {

        private final Project project;

        public ProjectUpdateResultMsg(User requester, Event request, Project project) {
            super(requester, request);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            this.project = project;
        }

        public Project getProject() {
            return project;
        }
    }

    class ProjectUpdateNotAuthoriseMsg extends EventRequestMessage {

        private final Project project;

        public ProjectUpdateNotAuthoriseMsg(User requester, Event request, Project project) {
            super(requester, request);
            if (project == null) {
                throw new IllegalArgumentException("project must be defined.");
            }
            this.project = project;
        }

        public Project getProject() {
            return project;
        }
    }

    class ListAndUpdateUserToProjectMsg extends EventRequestMessage {

        private final UpdateData<User> user;

        public ListAndUpdateUserToProjectMsg(User requester, Event request, UpdateData<User> user) {
            super(requester, request);
            requireNonNull(user, "user must be defined.");
            this.user = user;
        }

        public UpdateData<User> getUser() {
            return user;
        }
    }

    class ListAndUpdateUserToProjectResultMsg extends EventRequestMessage {

        private final ListAndUpdateUserToProjectMsg request;

        private final boolean success;

        public ListAndUpdateUserToProjectResultMsg(User requester, Event eventRequest, ListAndUpdateUserToProjectMsg request, boolean success) {
            super(requester, eventRequest);
            requireNonNull(request, "request must be defined.");
            this.request = request;
            this.success = success;
        }

        public ListAndUpdateUserToProjectMsg getRequest() {
            return request;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
