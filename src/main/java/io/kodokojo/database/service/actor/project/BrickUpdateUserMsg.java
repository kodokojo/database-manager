package io.kodokojo.database.service.actor.project;

import io.kodokojo.commons.model.*;
import io.kodokojo.commons.model.TypeChange;

import java.util.List;

public class BrickUpdateUserMsg {

    private final ProjectConfiguration projectConfiguration;

    private final StackConfiguration stackConfiguration;

    private final BrickConfiguration brickConfiguration;

    private final List<UpdateData<User>> users;

    private final io.kodokojo.commons.model.TypeChange typeChange;

    public BrickUpdateUserMsg(TypeChange typeChange, List<UpdateData<User>> users, ProjectConfiguration projectConfiguration, StackConfiguration stackConfiguration, BrickConfiguration brickConfiguration) {
        this.users = users;
        this.typeChange = typeChange;
        if (projectConfiguration == null) {
            throw new IllegalArgumentException("projectConfiguration must be defined.");
        }
        if (stackConfiguration == null) {
            throw new IllegalArgumentException("stackConfiguration must be defined.");
        }
        if (brickConfiguration == null) {
            throw new IllegalArgumentException("brickConfiguration must be defined.");
        }
        this.projectConfiguration = projectConfiguration;
        this.stackConfiguration = stackConfiguration;
        this.brickConfiguration = brickConfiguration;
    }

    public ProjectConfiguration getProjectConfiguration() {
        return projectConfiguration;
    }

    public StackConfiguration getStackConfiguration() {
        return stackConfiguration;
    }

    public BrickConfiguration getBrickConfiguration() {
        return brickConfiguration;
    }

    public List<UpdateData<User>> getUsers() {
        return users;
    }

    public TypeChange getTypeChange() {
        return typeChange;
    }
}
