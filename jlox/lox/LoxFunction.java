package lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Token name;
    private final Expr.Function declaration;
    private final Environment closure;

    LoxFunction(Token name, Expr.Function declaration, Environment closure) {
        this.name = name;
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        if (name== null) return "<fn>";
        return "<fn " + name.lexeme + ">";
    }

    @Override
    public Object call(Interpreter interpreter,
                        List<Object> arguments) {

        // Creates new environment based on closure
        // Copies arguments into environment
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i),
                arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        return null;
    }
}
