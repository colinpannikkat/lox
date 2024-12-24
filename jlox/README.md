# Crafting Interpreters - JLox
This is my implementation of the Lox language and JLox interpreter/transpiler based on the book ["Crafting Interpreters"](http://www.craftinginterpreters.com/).

There are some differences between my implementation of the language, and the book Lox. I have added the following to the language, mainly based on book challenges, and my own desires:

* Ternary operator support (`?:`)
* Prefix increment/decrement and postfix increment/decrement operator support (`--`, `++`)
  * For this I follow the same philosophy as C for the postfix operators, in that the original value of the variable is returned, and then the variable is incremented.
* Compound assignment operators (`+=`, `-=`, `*=`, `/=`)
* String concatenation and automatic casting for string operations
* Comma separated printing for the built-in primitive print statement
* Newline unary operator for primitive strings, `\`
* Primitive function `println()`
* More robust syntax error parsing
* `continue` and `break` for loops
  * Separate implementations for parsing `for` and `while` statements
* Comma separated evaluation of conditions, expressions, and assignment
* Anonymous functions
* No re-declaration of variables

## Building
Ensure that you have a Java runtime installed on your device, and run the following to build.

```bash
./build.sh
```

## Grammar

Lox has a context-free grammar, and the interpreter uses a recursive descent parser for this reason since we can easily parse the non-terminal symbols of the grammar by building the nodes of an abstract syntax tree from the top-down. By structuring the parsing logic to handle grammar from the lowest precedence to highest when parsing, we ensure operators of higher precedence are deeper in the tree.

| Grammar Notation | Code Representation             |
| ---------------- | ------------------------------- |
| Terminal         | Code to match and consume token |
| Nonterminal      | Call to that rule's function    |
| '\|'             | `if` or `switch` statement      |
| '*' or '+'       | `while` or `for` loop           |
| '?'              | `if` statement                  |

```ebnf
program         = declaration* EOF ;
declaration     = funDecl
                | varDecl
                | statement ;
funDecl         = "fun" function ;
function        = IDENTIFIER "(" parameters? ")" block ;
parameters      = IDENTIFIER ( "," IDENTIFIER )* ;
varDecl         = "var" IDENTIFIER ( "=" conditional )? ";" ;
statement       = exprStmt
                | forStmt
                | interruptStmt
                | ifStmt
                | printStmt
                | whileStmt
                | block ;
forStmt         = "for" "(" ( varDecl | exprStmt | ";" )
                expression? ";"
                expression? ")" statement ;
interruptStmt   = "return" expression? ";" 
                | "break" ";" 
                | "continue" ";" ;
exprStmt        = expression ";" ;
ifStmt          = "if" "(" expression ")" statement
                ( "else" statement )? ;
printStmt       = "print" expression ";" ;
whileStmt       = "while" "(" expression ")" statement ;
block           = "{" declaration* "}" ;
expression      = comma ;
comma           = assignment ( ',' assignment )*
                | ( "?" expression ":" conditional )? ;
assignment      = IDENTIFER ("=" | "+=" | "-=" | "*=" | "/=" ) assignment
                | logic_or ;
logic_or        = logic_and ( "or" logic_and )* ;
logic_and       = equality ( "and" equality )* ;
conditional     = assignment ( "?" expression ":" expression )? ;
equality        = comparison ( ( "!=" | "==" ) comparison )* ;
comparison      = term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term            = factor ( ( "-" | "+" ) factor )* ;
factor          = unary ( ( "/" | "*" ) unary )* ;
unary           = ( "!" | "-" ) unary
                | ( "--" | "++" ) primary
                | postfix ;
postfix         = call ( '--' | '++' | '\' )? ;
call            = primary ( "(" arguments? ")" )* ;
arguments       = assignment ( "," assignment )* ;
primary         = "true" | "false" | "nil"
                | NUMBER | STRING
                | "(" expression ")"
                | IDENTIFIER ;
```

## Operator Precedence and Associativity

| Operators                    | Associativity | Description                        |
| ---------------------------- | ------------- | ---------------------------------- |
| `()`                         | Left-to-Right | Function calls                     |
| `--`, `++`, `\` (postfix)    | Left-to-Right | Postfix increment, decrement       |
| `--`, `++`, `!`, `-` (unary) | Right-to-Left | Prefix increment, decrement, unary |
| `*`, `/`                     | Left-to-Right | Multiplication, division           |
| `+`, `-`                     | Left-to-Right | Addition, subtraction              |
| `>`, `>=`, `<`, `<=`         | Left-to-Right | Comparison operators               |
| `==`, `!=`                   | Left-to-Right | Equality operators                 |
| `and`                        | Left-to-Right | Logical AND                        |
| `or`                         | Left-to-Right | Logical OR                         |
| `? :`                        | Right-to-Left | Conditional expression             |
| `=`, `+=`, `-=`, `*=`, `/=`  | Right-to-Left | Assignment                         |
| `,`                          | Left-to-Right | Comma                              |


## Reserved Keywords and Symbols

| Keyword/Symbol | Token         |
| -------------- | ------------- |
| and            | AND           |
| break          | BREAK         |
| continue       | CONTINUE      |
| class          | CLASS         |
| else           | ELSE          |
| false          | FALSE         |
| for            | FOR           |
| fun            | FUN           |
| if             | IF            |
| nil            | NIL           |
| or             | OR            |
| print          | PRINT         |
| return         | RETURN        |
| super          | SUPER         |
| this           | THIS          |
| true           | TRUE          |
| var            | VAR           |
| while          | WHILE         |
| \              | BACK_SLASH    |
| (              | LEFT_PAREN    |
| )              | RIGHT_PAREN   |
| {              | LEFT_BRACE    |
| }              | RIGHT_BRACE   |
| ,              | COMMA         |
| .              | DOT           |
| ;              | SEMICOLON     |
| \              | BACK_SLASH    |
| !              | BANG          |
| !=             | BANG_EQUAL    |
| =              | EQUAL         |
| ==             | EQUAL_EQUAL   |
| >              | GREATER       |
| >=             | GREATER_EQUAL |
| <              | LESS          |
| <=             | LESS_EQUAL    |
| /              | SLASH         |
| /=             | SLASH_EQUAL   |
| *              | STAR          |
| *=             | STAR_EQUAL    |
| +              | PLUS          |
| ++             | PLUS_PLUS     |
| +=             | PLUS_EQUAL    |
| -              | MINUS         |
| --             | MINUS_MINUS   |
| -=             | MINUS_EQUAL   |
| ?              | QUESTION      |
| :              | COLON         |