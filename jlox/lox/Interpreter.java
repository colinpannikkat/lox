package lox;

import static lox.TokenType.*;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Interpreter implements Expr.Visitor<Object>,
                                    Stmt.Visitor<Void> {

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();
    private static Object uninitialized = new Object();

    private static class Break extends RuntimeException {}
    private static class Continue extends RuntimeException {}

    Interpreter() {
        Token primitiveToken = new Token(IDENTIFIER, "clock", null, -1);
        globals.define(primitiveToken, new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
        
        primitiveToken = new Token(IDENTIFIER, "println", null, -1);
        globals.define(primitiveToken, new LoxCallable() {
           @Override
           public int arity() { return 1; } 

           @Override
           public Object call(Interpreter interpreter,
                              List<Object> arguments) {
                System.out.println(stringify(arguments.get(0)));
                return this;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    // Expression evaluators, returns expression value
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // Executor for statements
    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    // Variable resolution
    // Tells interpreter the number of scopes between current and scope where
    // the variable is defined
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    /* Statement node visitors */

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt.name, stmt.function, environment);
        environment.define(stmt.name, function);
        return null;
    }

    @Override
    public Void visitInterruptStmt(Stmt.Interrupt stmt) {
        switch (stmt.keyword.type) {
            case BREAK:
                throw new Break();
            case CONTINUE:
                throw new Continue();
            case RETURN:
                Object value = null;
                if (stmt.value != null) value = evaluate(stmt.value);
                throw new Return(value);
        }
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.print(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                try {
                    execute(stmt.body);
                } catch (Continue ex) {
                    continue;
                }
            }
        } catch (Break ex) {}
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        try {
            if (stmt.initializer != null) execute(stmt.initializer);
            while (isTruthy(evaluate(stmt.condition))) {
                try {
                    execute(stmt.body);
                } catch (Continue ex) {}
                if (stmt.increment != null) evaluate(stmt.increment);
            }
        } catch (Break ex) {}
        return null;
    }

    /* All expression node visitors */

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {

        Object right;
        /* 
         *  We know expressions must be variables for these two cases,
         *  so do not evaluate right side, and add or subtract one.
         */
        switch(expr.operator.type) {
            case MINUS_MINUS:
                return addToVariable(((Expr.Variable)expr.right), true, expr.operator, -1.0, true);
            case PLUS_PLUS:
                return addToVariable(((Expr.Variable)expr.right), true, expr.operator, 1.0, true);
        }

        right = evaluate(expr.right);

        switch(expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr.Variable expr) {
        Integer distance = locals.get(expr);
        Object value = null;
        if (distance != null) {
            value = environment.getAt(distance, name.lexeme);
        } else { 
            value = globals.get(name);
        }
        if (value == uninitialized) {
            throw new RuntimeError(expr.name, 
                "'" + expr.name.lexeme + "' used without initialization.");
        }
        return value;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment);
    }

    @Override
    public Object visitPostfixExpr(Expr.Postfix expr) {
        Object left;

        /* 
         *  We know expressions must be variables for these two cases,
         *  so do not evaluate left side, and add or subtract one.
         */
        switch(expr.operator.type) {
            case MINUS_MINUS:
                return addToVariable(((Expr.Variable)expr.left), 
                            true, expr.operator, -1.0, false);
            case PLUS_PLUS:
                return addToVariable(((Expr.Variable)expr.left), 
                            true, expr.operator, 1.0, false);

        }

        left = evaluate(expr.left);

        switch(expr.operator.type) {
            case BACK_SLASH:
                if (left instanceof String)
                    return (String)((String)left + '\n');
                throw new RuntimeError(expr.operator, "'\\' can only be used on strings.");
        }

        return null;
    }
    
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {

        Object value = evaluate(expr.value);
        Object oldvalue = null;
        Token operator = expr.operator;

        Integer distance = locals.get(expr);
        if (distance != null) {
            oldvalue = environment.getAt(distance, expr.name.lexeme);
        } else {
            oldvalue = globals.get(expr.name);
        }

        switch (operator.type) {
            /* Regular assignment */
            case EQUAL:
                break;
            /* Compound assignment */
            case PLUS_EQUAL:
                value = add(oldvalue, value, operator);
                break;
            case MINUS_EQUAL:
                checkNumberOperands(operator, value, oldvalue);
                value = (double)oldvalue - (double)value;
                break;
            case STAR_EQUAL:
                checkNumberOperands(operator, value, oldvalue);
                value = (double)oldvalue * (double)value;
                break;
            case SLASH_EQUAL:
                checkNumberOperands(operator, value, oldvalue);
                value = (double)oldvalue / (double)value;
                break;
        }

        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch(expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                return add(left, right, expr.operator);
               
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case COMMA:
                // Allow printing of strings via comma
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + "" + stringify(right);
                }
                
                return right;
        }

        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                function.arity() + " arguments, but got " + 
                arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        Object left = evaluate(expr.left);
        Object middle = evaluate(expr.middle);
        Object right = evaluate(expr.right);

        if (expr.operator1.type == QUESTION && expr.operator2.type == COLON) {
            if (isTruthy(left)) {
                return middle;
            }
            return right;
        }

        return null;
    }

    /* Group of helper functions for variables, type checking, and conversions */
    
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "'" + operator.lexeme + "' " + 
                "operand must be numbers.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
    
        throw new RuntimeError(operator, "'" + operator.lexeme + "' " + 
                "operands must be a number.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";
    
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
    
        return object.toString();
    }

    /*
     * Helper function for adding two operands.
     * Evaluates whether we are adding numbers, or concatenating strings.
     */
    private Object add(Object a, Object b, Token operator) {
        if (a instanceof Double && b instanceof Double) {
            return (double)a + (double)b;
        }
        if (a instanceof String || b instanceof String) {
            return stringify(a) + stringify(b);
        }
        
        throw new RuntimeError(operator,
                "'+' operands must be numbers or strings");
    }

    /*
     * Helper function that adds addValue to the existing value of expr.
     * checkNum exists to ensure that the variable is a number, and the function
     * can return updated value or old value to aid in prefix versus postfix 
     * expression evaluation.
     */
    private Double addToVariable(Expr.Variable expr, boolean checkNum, 
                            Token operator, Double addValue, boolean retAdded) {
        Object value = environment.get(expr.name);
        if (checkNum) checkNumberOperand(operator, value);
        environment.assign(expr.name, (Double)value + addValue);
        if (retAdded) return (Double)value + addValue;
        return (Double)value;
    }

}
