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
package io.kodokojo.database.service.actor.project;

import akka.actor.Actor;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import io.kodokojo.commons.model.Project;
import io.kodokojo.commons.model.ProjectConfiguration;
import io.kodokojo.commons.model.User;
import io.kodokojo.commons.service.repository.ProjectRepository;
import io.kodokojo.test.DataBuilder;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectUpdaterActorTest implements DataBuilder {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setup() {
        actorSystem = ActorSystem.create();
    }

    @Test
    public void update_project_from_unknown_requester() {
        // Given
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        TestActorRef<Actor> subject = TestActorRef.create(actorSystem, ProjectUpdaterActor.props(projectRepository));

        // When
        Patterns.ask(subject, new ProjectUpdaterMessages.ProjectUpdateMsg(null, null, aProjectWithStacks(aBuildStack())), DataBuilder.thirtySeconds);

        // Then
        Mockito.verify(projectRepository).updateProject(Matchers.any(Project.class));
    }

    @Test
    public void update_project_from_authorized_requester() {
        // Given
        User user = anUser();
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        ProjectConfiguration projectConfiguration = Mockito.mock(ProjectConfiguration.class);
        Mockito.when(projectRepository.getProjectConfigurationById("1234")).thenReturn(projectConfiguration);
        Mockito.when(projectConfiguration.getTeamLeaders()).thenReturn(Collections.singletonList(user).iterator());

        TestActorRef<Actor> subject = TestActorRef.create(actorSystem, ProjectUpdaterActor.props(projectRepository));

        // When
        Future<Object> future = Patterns.ask(subject, new ProjectUpdaterMessages.ProjectUpdateMsg(user, null, aProjectWithStacks(aBuildStack())), DataBuilder.thirtySeconds);

        try {
            Object result = Await.result(future, DataBuilder.twoSeconds);
            assertThat(result.getClass()).isEqualTo(ProjectUpdaterMessages.ProjectUpdateResultMsg.class);

            ProjectUpdaterMessages.ProjectUpdateResultMsg msg = (ProjectUpdaterMessages.ProjectUpdateResultMsg) result;
            assertThat(msg.getProject()).isNotNull();
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }

        // Then
        Mockito.verify(projectRepository).updateProject(Matchers.any(Project.class));
    }

    @AfterClass
    public static void tearDown() {
        JavaTestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

}