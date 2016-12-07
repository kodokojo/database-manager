package io.kodokojo.database.service.actor;

import akka.actor.ActorRef;
import io.kodokojo.commons.dto.ProjectConfigurationCreationDto;
import io.kodokojo.commons.dto.UserUpdateDto;
import io.kodokojo.commons.event.Event;
import io.kodokojo.commons.event.payload.UserCreationRequest;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.actor.AbstractEventToActorGateway;
import io.kodokojo.commons.service.actor.message.EventBusOriginMessage;
import io.kodokojo.commons.service.repository.UserFetcher;
import io.kodokojo.database.service.actor.project.ProjectConfigurationBuilderActor;
import io.kodokojo.database.service.actor.user.UserCreatorActor;
import io.kodokojo.database.service.actor.user.UserGenerateIdentifierActor;
import io.kodokojo.database.service.actor.user.UserMessage;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class EventToActorGateway extends AbstractEventToActorGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventToActorGateway.class);


    @Inject
    public EventToActorGateway(ActorRef endpoint, UserFetcher userFetcher) {
        super(endpoint, userFetcher);
    }

    @Override
    protected EventBusOriginMessage messageToTell(Event event, User requester) {
        EventBusOriginMessage msg = null;
        if (event != null) {
            switch (event.getEventType()) {
                case Event.USER_IDENTIFIER_CREATION_REQUEST:
                    msg = new UserGenerateIdentifierActor.UserGenerateIdentifierMsg(event);
                    break;
                case Event.USER_CREATION_REQUEST:
                    UserCreationRequest creationRequest = event.getPayload(UserCreationRequest.class);
                    msg = new UserCreatorActor.EventCreateMsg(requester, event, creationRequest.getId(), creationRequest.getEmail(), creationRequest.getUsername(), creationRequest.getEntityId());
                    break;
                case Event.USER_UPDATE_REQUEST:
                    UserUpdateDto userDto = event.getPayload(UserUpdateDto.class);
                    User userToUpdate = userFetcher.getUserByIdentifier(userDto.getIdentifier());
                    msg = new UserMessage.UserUpdateMessage(requester, event, userToUpdate, userDto.getPassword(), userDto.getSshPublicKey(), userDto.getFirstName(), userDto.getLastName(), userDto.getEmail());
                    break;
                case Event.PROJECTCONFIG_CREATION_REQUEST:
                    ProjectConfigurationCreationDto projectConfigurationCreationDto = event.getPayload(ProjectConfigurationCreationDto.class);
                    msg = new ProjectConfigurationBuilderActor.ProjectConfigurationBuildMsg(requester, event, projectConfigurationCreationDto);
                    break;
            /*
            case Event.PROJECTCONFIG_CHANGE_USER_REQUEST:
                ProjectConfigurationChangeUserRequest changeUserRequest = event.getPayload(ProjectConfigurationChangeUserRequest.class);
                msg = new ProjectConfigurationChangeUserActor.ProjectConfigurationChangeEventMsg(requester, event, TypeChange.valueOf(changeUserRequest.getTypeChange().toString()), changeUserRequest.getProjectConfigurationId(), changeUserRequest.getUserIdentifiers());
                break;
                */
                default:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Not recognize event type '{}' from following event, drop it : {}", event.getEventType(), Event.convertToJson(event));
                    }
                    break;
            }
        }
        return msg;
    }
}
