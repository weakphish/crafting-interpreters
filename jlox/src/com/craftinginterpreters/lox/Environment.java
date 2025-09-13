package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, Object> values = new HashMap<>();

    Object get(Token name) {
        if (values.containsKey(name.lexeme())) {
            return values.get(name.lexeme());
        }

        throw new RuntimeError(name, "Undefined variable: " + name.lexeme() + ".");
    }

    void define(String name, Object value) {
        // note the semantic choice here - we don't care about variable re-definition. If we wanted to, we could modify
        // that here
        values.put(name, value);
    }
}
