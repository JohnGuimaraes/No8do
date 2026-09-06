package com.no8do.api.replay;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;
import java.util.UUID;

public class UpdateReplayRequest {

    private String title;
    private ReplayType type;
    private String problem;
    private String solution;
    private String context;
    private List<String> tags;
    private List<String> stack;
    private ReplayStatus status;
    private UUID projectId;
    private boolean titlePresent;
    private boolean typePresent;
    private boolean problemPresent;
    private boolean solutionPresent;
    private boolean contextPresent;
    private boolean tagsPresent;
    private boolean stackPresent;
    private boolean statusPresent;
    private boolean projectIdPresent;

    public UpdateReplayRequest() {}

    public UpdateReplayRequest(String title, ReplayType type, String problem, String solution, String context,
            List<String> tags, List<String> stack, ReplayStatus status, UUID projectId) {
        setTitle(title);
        setType(type);
        setProblem(problem);
        setSolution(solution);
        setContext(context);
        setTags(tags);
        setStack(stack);
        setStatus(status);
        setProjectId(projectId);
    }

    @JsonProperty("title") public String title() { return title; }
    @JsonProperty("type") public ReplayType type() { return type; }
    @JsonProperty("problem") public String problem() { return problem; }
    @JsonProperty("solution") public String solution() { return solution; }
    @JsonProperty("context") public String context() { return context; }
    @JsonProperty("tags") public List<String> tags() { return tags; }
    @JsonProperty("stack") public List<String> stack() { return stack; }
    @JsonProperty("status") public ReplayStatus status() { return status; }
    @JsonProperty("projectId") public UUID projectId() { return projectId; }

    @JsonSetter("title") public void setTitle(String value) { title = value; titlePresent = true; }
    @JsonSetter("type") public void setType(ReplayType value) { type = value; typePresent = true; }
    @JsonSetter("problem") public void setProblem(String value) { problem = value; problemPresent = true; }
    @JsonSetter("solution") public void setSolution(String value) { solution = value; solutionPresent = true; }
    @JsonSetter("context") public void setContext(String value) { context = value; contextPresent = true; }
    @JsonSetter("tags") public void setTags(List<String> value) { tags = value; tagsPresent = true; }
    @JsonSetter("stack") public void setStack(List<String> value) { stack = value; stackPresent = true; }
    @JsonSetter("status") public void setStatus(ReplayStatus value) { status = value; statusPresent = true; }
    @JsonSetter("projectId") public void setProjectId(UUID value) { projectId = value; projectIdPresent = true; }

    @JsonIgnore public boolean hasTitle() { return titlePresent; }
    @JsonIgnore public boolean hasType() { return typePresent; }
    @JsonIgnore public boolean hasProblem() { return problemPresent; }
    @JsonIgnore public boolean hasSolution() { return solutionPresent; }
    @JsonIgnore public boolean hasContext() { return contextPresent; }
    @JsonIgnore public boolean hasTags() { return tagsPresent; }
    @JsonIgnore public boolean hasStack() { return stackPresent; }
    @JsonIgnore public boolean hasStatus() { return statusPresent; }
    @JsonIgnore public boolean hasProjectId() { return projectIdPresent; }
}
