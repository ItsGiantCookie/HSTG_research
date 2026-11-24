package io.github.itsgiantcookie.hstglabs.tech.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A named, parameterised preset for one kind of content (docs/design.md §8.6): a JSON template from
 * {@code config/hstg_labs/templates/} or a code template such as {@code hstg_labs:tiered}.
 */
public interface Template {

    String id();

    /** Template kind id, e.g. {@code hstg_labs:technology}. */
    String kind();

    /** The template this one extends. */
    Optional<String> parent();

    /** Params this template declares (in addition to its parents'). */
    Map<String, ParamSpec> declares();

    /** Param values this template sets for itself and its parents' params. */
    JsonObject params();

    /**
     * This template's part of the result, merged over its parent's. JSON templates return their
     * {@code defaults} with {@code ${param}} and {@code =} still in place; code templates compute from the
     * final params. {@code isDataPack} says whether an id is a loaded data-driven pack.
     */
    JsonObject body(Map<String, JsonElement> params, Predicate<String> isDataPack);

    /**
     * One declared param.
     *
     * @param defaultValue its default: a value of the type, or a string starting with {@code =} for an
     *                     expression over other params; empty = required
     */
    record ParamSpec(String name, ParamType type, Optional<JsonElement> defaultValue) {
    }
}
