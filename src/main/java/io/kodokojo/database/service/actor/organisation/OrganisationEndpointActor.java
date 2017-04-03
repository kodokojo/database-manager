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

public class OrganisationEndpointActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static Props PROPS(OrganisationRepository organisationRepository) {
        requireNonNull(organisationRepository, "entityRepository must be defined.");
        return Props.create(OrganisationEndpointActor.class, organisationRepository);
    }

    public static final String NAME = "organisationEndpointProps";

    public OrganisationEndpointActor(OrganisationRepository organisationRepository) {

        receive(ReceiveBuilder.match(
                OrganisationMessage.ChangeUserToOrganisationMsg.class,
                msg -> getContext().actorOf(ChangeUserToOrganisationActor.PROPS(organisationRepository)).forward(msg, getContext())
        ).match(
                OrganisationCreatorActor.OrganisationCreateMsg.class,
                msg -> getContext().actorOf(OrganisationCreatorActor.PROPS(organisationRepository)).forward(msg, getContext())
        ).matchAny(this::unhandled).build());

    }


}
