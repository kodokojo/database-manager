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
import io.kodokojo.commons.service.repository.OrganisationRepository;

import static akka.event.Logging.getLogger;
import static java.util.Objects.requireNonNull;

public class ChangeUserToOrganisationActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final OrganisationRepository organisationRepository;

    public static Props PROPS(OrganisationRepository organisationRepository) {
        requireNonNull(organisationRepository, "organisationRepository must be defined.");
        return Props.create(ChangeUserToOrganisationActor.class, organisationRepository);
    }

    public ChangeUserToOrganisationActor(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;

        receive(ReceiveBuilder
                .match(OrganisationMessage.ChangeUserToOrganisationMsg.class, this::onAddUserToOrganisationMsg)
                .matchAny(this::unhandled)
                .build());
    }

    private void onAddUserToOrganisationMsg(OrganisationMessage.ChangeUserToOrganisationMsg msg) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Change organisation {} action: {} on user {} admin:{}", msg.organisationId, msg.typeChange, msg.userId, msg.admin);
        }
        switch (msg.typeChange) {
            case ADD:
                if (msg.admin) {
                    organisationRepository.addAdminToOrganisation(msg.userId, msg.organisationId);
                } else {
                    organisationRepository.addUserToOrganisation(msg.userId, msg.organisationId);
                }
                break;
            case REMOVE:
                if (msg.admin) {
                    organisationRepository.removeAdminToOrganisation(msg.userId, msg.organisationId);
                } else {
                    organisationRepository.removeUserToOrganisation(msg.userId, msg.organisationId);
                }
                break;
        }
        sender().tell(Boolean.TRUE, self());
        getContext().stop(self());
    }


}
