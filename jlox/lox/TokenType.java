package lox;

enum TokenType {
    // Single-character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, 
    COMMA, DOT, SEMICOLON, BACK_SLASH,

    // One, two, or three char
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    SLASH, SLASH_EQUAL,
    STAR, STAR_EQUAL,
    PLUS, PLUS_PLUS, PLUS_EQUAL,
    MINUS, MINUS_MINUS, MINUS_EQUAL,

    // Ternary (x ? y : z)
    QUESTION,
    COLON,

    // Literals
    IDENTIFIER, STRING, NUMBER,

    // Keywords
    AND, BREAK, CONTINUE, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF
}