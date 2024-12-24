package lox;

import java.util.ArrayList;
import java.util.List;

import static lox.TokenType.*;

/*
 * Grammer notation     |       Code Representation
 * ----------------------------------------------------------------------------
 * Terminal             |       Code to match and consume token
 * Nonterminal          |       Call to that rule's function
 * '|'                  |       `if` or `switch` statement
 * '*' or '+'           |       `while` or `for` loop
 * '?'                  |       if statement
 *
 * program -> declaration* EOF ;
 * declaration -> funDecl
 *               | varDecl
 *               | statement ;
 * funDecl -> "fun" function ;
 * function -> IDENTIFIER? "(" parameters? ")" block;
 * parameters -> IDENTIFIER ( "," IDENTIFIER )* ;
 * varDecl -> "var" IDENTIFIER ( "=" conditional )? ";" ;
 * statement -> exprStmt
 *              | forStmt
 *              | interruptStmt
 *              | ifStmt
 *              | printStmt
 *              | whileStmt
 *              | block ;
 * forStmt -> "for" "(" ( varDecl | exprStmt | ";" )
 *             expression? ";"
 *             expression? ")" statement ;
 * interruptStmt -> "return" expression ? ";" | "break" ";" | "continue" ";" ;
 * exprStmt -> expression ";" ;
 * ifStmt -> "if" "(" expression ")" statement
 *            ( "else" statement )? ;
 * printStmt -> "print" expression ";" ;
 * whileStmt -> "while" "(" expression ")" statement ;
 * block -> "{" declaration* "}" ;
 * expression -> comma ;
 * comma -> assignment ( ',' assignment )*
 *                     | ( "?" expression ":" conditional )?;
 * assignment -> IDENTIFER ("=" | "+=" | "-=" | "*=" | "/=" ) assignment"
 *               | logic_or ;
 * logic_or -> logic_and ( "or" logic_and )* ;
 * logic_and -> equality ( "and" equality )* ; 
 * conditional -> assignment ( "?" expression ":" expression )?;
 * equality -> comparison ( ( "!=" | "==" ) comparison)*;
 * comparison -> term ( (  ">" | ">=" | "<" | "<=" ) term )* ;
 * term -> factor ( ( "-" | "+" ) factor )* ;
 * factor -> unary ( ( "/" | "*" ) unary )* ;
 * unary -> ("!" | "-" ) unary
 *          | ( "--" | "++" ) primary
 *          | postfix;
 * postfix -> call ('--' | '++' | '\')? ;
 * call -> primary ( "(" arguments? ")" )* ;
 * arguments -> assignment ( "," assignment )* ;
 * primary -> "true" | "false" | "nil"
 *           | NUMBER | STRING
 *           | "(" expression ")"
 *           | FUN
 *           | IDENTIFIER ;
 *           // error production
 *           | ("!=" | "== ") equality
 *           | (">" | ">=" | "<" | "<=") comparison
 *           | ("+" ) term
 *           | ("/" | "* ") factor ; 
 */

public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private int loopDepth = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    /*
     *  declaration -> funDecl
     *              | varDecl
     *              | statement ;
     */
    private Stmt declaration() {
        try {
            // We process, if anonymous function send to statement which will cascade down
            // and check in primary()
            if (check(FUN) && checkNext(IDENTIFIER)) return function("function");
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize(); // synchronize to next statement
            return null;
        }
    }

    /*
     *  statement -> exprStmt
     *              | printStmt 
     *              | block ;
     */
    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(RETURN, BREAK, CONTINUE)) return interruptStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    /* EXTRA
     * forStmt -> "for" "(" ( varDecl | exprStmt | ";" )
     *           expression? ";"
     *           expression? ")" statement ;
     */

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");
        if (condition == null) condition = new Expr.Literal(true);

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");       

        Stmt body = null;
        try {
            loopDepth++;
            body = statement();
        } finally {
            loopDepth--;
        }

        return new Stmt.For(initializer, condition, increment, body);
    }

    /* EXTRA
     * interruptStmt -> "return" expression? ";" | "break" ";" | "continue" ";" ;
     */
    private Stmt interruptStatement() {
        Token name = previous();
        Expr value = null;

        switch (name.type) {
            case RETURN:
                if (!check(SEMICOLON)) {
                    value = expression();
                }
                consume(SEMICOLON, "Expect ';' after return value;");
                return new Stmt.Interrupt(name, value);                
            case CONTINUE:
            case BREAK:
                if (loopDepth == 0) {
                    error(previous(), 
                        "Must be inside a loop to use 'break' or 'continue'.");
                }
                consume(SEMICOLON, "Expect ';' after 'break'.");
                return new Stmt.Interrupt(name, value);                
        }
        return null;
    }

    /*
     *  ifStmt -> "if" "(" expression ")" statement
     *            ( "else" statement )? ;
     */
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'if' condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /*
     * printStmt -> "print" expression ";" ;
     */
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    /*
     * whileStmt -> "while" "(" expression ")" statement ;
     */
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition");

        try {
            loopDepth++;
            Stmt body = statement();
            return new Stmt.While(condition, body);
        } finally {
            loopDepth--;
        }
    }

    /*
     * block -> "{" declaration* "}" ;
     */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while(!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    /*
     * varDecl -> "var" IDENTIFIER ( ( "=" conditional ) )? ";" ;
     */
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = conditional();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    /*
     * exprStmt -> expression ";" ;
     */
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }

     /*
     * funDecl -> "fun" function ;
     * function -> IDENTIFIER? "(" parameters? ")" block ;
     */
    private Stmt.Function function(String kind) {
        // Consume var
        consume(FUN, "Function declaration requires fun");
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        return new Stmt.Function(name, functionBody(kind));
    }

    /*
     * Separate function that allows us to process anonymous functions
     * as expressions.
     */
    private Expr.Function functionBody(String kind) {
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

        /* Parameter list */
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters");
                }

                parameters.add(
                    consume(IDENTIFIER, "Expect parameter name"));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        /* Function body */
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List <Stmt> body = block();

        return new Expr.Function(parameters, body);
    }

    /* 
     * expression -> comma ;
     */
    private Expr expression() {
        return comma();
    }

    /* EXTRA
     * comma ->  assignment ( ',' assignment )*
     *                      | ( "?" expression ":" conditional )?;
     */
    private Expr comma() {
        Expr expr = assignment();

        /* Comma separated */
        while (match(COMMA)) {
            Token operator = previous();
            Expr right = assignment();
            expr = new Expr.Binary(expr, operator, right);
        }
        /* Conditional */
        if (match(QUESTION)) {
            Token operator1 = previous();
            Expr thenBranch = expression();
            consume(COLON,
                "Expect ':' after then branch of conditional expression.");
            Token operator2 = previous();
            Expr elseBranch = conditional();
            expr = new Expr.Ternary(expr, operator1, thenBranch, operator2, elseBranch);
        }

        return expr;
    }

    /*
     * assignment -> IDENTIFER ("=" | "+=" | "-=" | "*=" | "/=" ) assignment
     *               | logic_or ;
     */
    private Expr assignment() {
        Expr expr = or();
        
        if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL)) {
            Token assignment = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, assignment, value);
            }

            error(assignment, "Invalid assignment target");
        }

        return expr;
    }

    /*
     * logic_or -> logic_and ( "or" logic_and )* ;
     */
    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }


    /*
     * logic_and -> equality ( "and" equality )* ;
     */
    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /* EXTRA
     * conditional -> assignment ( "?" expression ":" expression )?;
     */
    private Expr conditional() {
        Expr expr = assignment();
        
        if (match(QUESTION)) {
            Token operator1 = previous();
            Expr thenBranch = expression();
            consume(COLON,
                "Expect ':' after then branch of conditional expression.");
            Token operator2 = previous();
            Expr elseBranch = expression();
            expr = new Expr.Ternary(expr, operator1, thenBranch, operator2, elseBranch);
        }
        
        return expr;
    }

    /*
     * equality -> comparison ( ( "!=" | "==" ) comparison)*;
     */
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * comparison -> term ( (  ">" | ">=" | "<" | "<=" ) term )* ;
     */
    private Expr comparison() {
        Expr expr = term();

        while (match(LESS, LESS_EQUAL, GREATER, GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * term -> factor ( ( "-" | "+" ) factor )* ;
     */
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * factor -> unary ( ( "/" | "*" ) unary )* ;
     */
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * unary -> ("!" | "-" ) unary
     *          | ( "--" | "++" ) primary
     *          | postfix;
     */
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        // Can only be used on lvalues (modifiable variables)
        if (match(MINUS_MINUS, PLUS_PLUS)) {
            Token operator = previous();
            Expr right = primary();
            if (!(right instanceof Expr.Variable)) {
                error(operator, "Can only increment or decrement variables.");
            }
            return new Expr.Unary(operator, right);
        }
        
        return postfix();
    }

    /* EXTRA
     * postfix -> call ('--' | '++' | '\')? ;
     */
    private Expr postfix() {
        Expr expr = call();
        
        if (match(MINUS_MINUS, PLUS_PLUS)) {

            if (!(expr instanceof Expr.Variable)) {
                error(previous(), "Can only increment or decrement variables.");
            }

            Token operator = previous();
            expr = new Expr.Postfix(expr, operator); 

            if (match(MINUS_MINUS, PLUS_PLUS)) {
                error(previous(), "Cannot concatenate operators '++' and '--'.");
            }    
        }

        /* Used for new-lines while printing */
        if (match(BACK_SLASH)) {
            Token operator = previous();
            expr = new Expr.Postfix(expr, operator);
        }

        return expr;
    }

    /*
     * call -> primary ( "(" arguments? ")" )* ;
     */
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }
       
        return expr;
    }

    /*
     * arguments -> assignment ( "," assignment )* ;
     * Use assignment over expression to had precedence over comma.
     */
    private Expr finishCall(Expr calle) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(assignment());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,
                        "Expect ')' after arguments.");

        return new Expr.Call(calle, paren, arguments);
    }

    /*
     * primary -> "true" | "false" | "nil"
     *           | NUMBER | STRING
     *           | "(" expression ")"
     *           | FUN
     *           | IDENTIFIER ;
     *           // error production
     *           | ("!=" | "== ") equality
     *           | (">" | ">=" | "<" | "<=") comparison
     *           | ("+" ) term
     *           | ("/" | "* ") factor ; 
     */
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(FUN)) {
            return functionBody("function");
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if (match(LEFT_BRACE)) {
            Expr expr = expression();
            consume(RIGHT_BRACE, "Expect '}' after expression.");
            return new Expr.Grouping(expr);
        }
    
        // Error productions.
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            equality();
            return null;
        }
    
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            comparison();
            return null;
        }
    
        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand.");
            term();
            return null;
        }
    
        if (match(SLASH, STAR)) {
            error(previous(), "Missing left-hand operand.");
            factor();
            return null;
        }

        throw error(peek(), "Expect expression");
    }

    /*
     * Checks for matching of current token across various types
     */
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /* Checks and consumes, if check isn't valid, throws syntax error. */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    /*
     * Checks for matching of current token across one type
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /*
     * Checks next token against one type
     */
    private boolean checkNext(TokenType type) {
        if (isAtEnd() || tokens.get(current + 1).type == EOF) return false;
        if (tokens.get(current + 1).type == type) return true;
        return false;
    }

    /* Advances on tokens */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /* Checks if at the end of tokens */
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    /* Peeks at current token, does not consume */
    private Token peek() {
        return tokens.get(current);
    }

    /* Gets previous token */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /* Throws a parsing syntax error */
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    /*
     * Here we are discarding tokens until we find a statement boundary,
     * when catching a ParseError, we call this and hopefully synchronize to
     * parse the rest of the file without issue.
     * 
     * This enables us to be in "panic mode", but still parse the rest of the
     * tokens so we can continue to analyze for any other errors without
     * dealing with "cascaded errors".
     */
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case BREAK:
                case CONTINUE:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
