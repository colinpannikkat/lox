package lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    /*
     * Getter for environment values
     */
    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            Object value = values.get(name.lexeme);
            return value;
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    /*
     * Assigner for existing environment values
     */
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
          values.put(name.lexeme, value);
          return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
    
        throw new RuntimeError(name,
            "Undefined variable '" + name.lexeme + "'.");
    }

    /*
     * Defines new value in environment
     */
    void define(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            throw new RuntimeError(name, 
                "Attempted redeclaration of '" + name.lexeme + "'.");
        }
        values.put(name.lexeme, value);
    }

    /*
     * Gets variable at specific enclosing environment
     */
    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    /*
     * Assigns variable at specific scope
     */
    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    /*
     * Gets environment that is 'distance away' from current scope
     */
    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        
        return environment;
    }
}
