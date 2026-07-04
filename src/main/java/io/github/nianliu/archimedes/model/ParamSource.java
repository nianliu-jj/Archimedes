package io.github.nianliu.archimedes.model;

/** 参数来源。FORM 为后续切片预留，切片一不产出。 */
public enum ParamSource {
    QUERY, PATH, BODY, HEADER, FORM, OTHER
}
