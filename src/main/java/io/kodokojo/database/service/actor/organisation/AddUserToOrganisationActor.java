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

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.commons.service.repository.OrganisationRepository;

import static java.util.Objects.requireNonNull;

public class AddUserToOrganisationActor extends AbstractActor {

    private final OrganisationRepository entityRepository;

    public AddUserToOrganisationActor(OrganisationRepository entityRepository) {
        this.entityRepository = entityRepository;

        receive(ReceiveBuilder
                .match(OrganisationMessage.AddUserToOrganisationMsg.class, this::onAddUserToEntityMesg)
                .matchAny(this::unhandled)
                .build());
    }

    private void onAddUserToEntityMesg(OrganisationMessage.AddUserToOrganisationMsg msg) {
        entityRepository.addUserToOrganisation(msg.userId, msg.entityId);
        getContext().stop(self());
    }

    public static Props PROPS(OrganisationRepository entityRepository) {
        requireNonNull(entityRepository, "entityRepository must be defined.");
        return Props.create(AddUserToOrganisationActor.class, entityRepository);
    }

}
