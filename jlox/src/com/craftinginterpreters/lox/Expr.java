package com.craftinginterpreters.lox;

/**
 * The base class for an expression doesn't implement any behaviroal methods because it's neither owned by the parser
 * nor the interpreter - it allows the parser and interpreter to communicate.
 */
abstract class Expr {
    static class Binary extends Expr {
        final Expr left;
        final Token operator;
        final Expr right;

        Binary(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
    }
}
