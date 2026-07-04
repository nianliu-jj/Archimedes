package io.github.nianliu.archimedes.model;

public class ParamInfo {

    private String name;
    private ParamSource source;
    private String type;
    private boolean required;

    public ParamInfo() {
    }

    public ParamInfo(String name, ParamSource source, String type, boolean required) {
        this.name = name;
        this.source = source;
        this.type = type;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParamSource getSource() {
        return source;
    }

    public void setSource(ParamSource source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
