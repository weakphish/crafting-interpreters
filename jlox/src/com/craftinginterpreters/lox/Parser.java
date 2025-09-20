package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Initial method to kick it all off.
     * Parse statements until we're at the end. Pretty close translation of the `program` grammar rule to recursively
     * parse.
     *
     * @return parsed statements
     */
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    /**
     * Method we call repeatedly when parsing a series of statements in a block or a script, so probably the right place
     * to synchronize when the parser goes into panic mode. Whole thing is wrapped in a try/catch, so if an exception is
     * thrown when parsing, the parser will recover to the next statement or declaration.
     *
     * @return The parsed declaration statement
     */
    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            // if no match for a declaration, fall thru to statement (recall precedence)
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        // if no match for a statement, fall thru to expression (recall precedence)
        return expressionStatement();
    }

    /**
     * Desugared!
     *
     * @return a For statemetn
     */
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // parse initializer
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // parse condition
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        // parse increment
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        // body
        Stmt body = statement();

        // The increment, if there is one, executes after the body in each iteration of the loop.
        // We do that by replacing the body with a little block that contains the original body followed
        // by an expression statement that evaluates the increment.
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        // Next, we take the condition and the body and build the loop using a primitive while loop.
        // If the condition is omitted, we jam in true to make an infinite loop.
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // Finally, if there is an initializer, it runs once before the entire loop.
        // We do that by, again, replacing the whole statement with a block that runs the initializer
        // and then executes the loop.
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

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

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Expression(expr);
    }

    /**
     * Gulp up all the statements into a list till we hit the matching brace.
     *
     * @return
     */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    /**
     * Something to note is that every valid assignment target works as a normal expressions.
     * We parse the left-hand side, which can be any expression of higher precedence.
     * If we find an =, parse the right-hand side and wrap it all up in an assignment expression node.
     * Instead of looping like other operators, recursively call assignment() to parse right-hand side.
     * We can parse the left-hand side as if it were an expression, and then produce a syntax tree that
     * turns it into an assignment target after the fact.
     *
     * @return Parsed assignment expression
     */
    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            // look at left-hand side to figure out what kind of assignment target this is
            Token equals = previous();
            Expr value = assignment();


            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
            // don't throw it because we need to synchronize, not explode
            error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value");
        return new Stmt.Print(value);
    }

    /**
     * As always, the recursive descent code follows the grammar rule.
     * The parser has already matched the var token, so next it requires and consumes an identifier token for the
     * variable name. Then, if it sees an = token, it knows there is an initializer expression and parses it.
     * Otherwise, it leaves the initializer null.
     * Finally, it consumes the required semicolon at the end of the statement.
     * All this gets wrapped in a Stmt.Var syntax tree node and we’re groovy.
     *
     * @return parsed variable declaration statement
     */
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    /**
     * We look at the current token to see how to parse.
     * If it’s a ! or -, we must have a unary expression.
     * In that case, we grab the token and then recursively call unary() again to parse the operand.
     * Wrap that all up in a unary expression syntax tree and we’re done.
     * Otherwise, we must have reached the highest level of precedence, primary expressions.
     *
     * @return an Expr
     */
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal());
        }

        // variable expression (not declaration)
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        // consume inner expression of the grouping, then the right paren
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // if we fell thru to here, nothing matched
        throw error(peek(), "Expect expression.");
    }

    /**
     * This checks to see if the current token has any of the given types.
     * If so, it consumes the token and returns true.
     * Otherwise, it returns false and leaves the current token alone.
     * The match() method is defined in terms of two more fundamental operations.
     *
     * @param types The types to match against
     * @return Whether the current token matches any of the given types
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

    /**
     * Checks to see if the next token is of some expected type.
     * If so, consume the token and everything is groovy.
     * If not, report an error.
     *
     * @param type    The expected token type
     * @param message Error message
     * @return the consumed token
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    /**
     * Check if the current token is of a given type
     *
     * @param type The type to check against
     * @return If the current token is of a given type
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    /**
     * Consume the current token and return it
     *
     * @return The consumed token
     */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == EOF;
    }

    /**
     * Get the current token w/o consuming
     *
     * @return The current token
     */
    private Token peek() {
        return tokens.get(current);
    }

    /**
     * Get the token prior to the current pointer.
     *
     * @return The prior token
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        // return an error instead of throwing so that the caller can decide to unwind or not
        return new ParseError();
    }

    /**
     * Discard tokens till we think we've found a statement boundary. After catching a ParseError, call this and get
     * back in sync.
     * NOTE: if we wanted an expression-only language, we would likely want to modify the boundary - what to?
     */
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type() == SEMICOLON) return;

            switch (peek().type()) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();

        }
    }

    /**
     * Simple sentinel class used to unwind the parser in a panic state.
     */
    private static class ParseError extends RuntimeException {
    }
}