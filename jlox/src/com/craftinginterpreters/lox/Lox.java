package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    static boolean hadError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    /**
     * Run a Lox file
     *
     * @param path path to the lox source code file
     * @throws IOException
     */
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if (hadError)
            System.exit(65);
    }

    /**
     * Run the interpeter interactively in a REPL-like environment.
     *
     * @throws IOException
     */
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (; ; ) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null)
                break;
            run(line);
            hadError = false; // reset so an error doesn't kill the session
        }
    }

    /**
     * Scan source and produce a list of tokens
     *
     * @param source the lox source code, as a string
     */
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        Expr expression = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        System.out.println(new AstPrinter().print(expression));
    }

    /**
     * Wrapper around report, reporting an error on a given line with a message
     *
     * @param line    The line the error occurred on
     * @param message A message to accompany the error
     */
    static void error(int line, String message) {
        report(line, "", message);
    }

    /**
     * Report an error and flag it in our object state
     *
     * @param line    The line the error occurred on
     * @param where   TODO
     * @param message Message to accompany the error
     */
    private static void report(int line, String where, String message) {
        System.err.println("[line " + "] Error" + where + ": " + message);
        hadError = true;
    }

    /**
     * Report an error at a given location. Show the token's location and the token itself.
     *
     * @param token   The error'd token
     * @param message The message to report
     */
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }
}
