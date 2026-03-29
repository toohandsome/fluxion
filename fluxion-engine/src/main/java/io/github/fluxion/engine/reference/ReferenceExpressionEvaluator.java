package io.github.fluxion.engine.reference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ReferenceExpressionEvaluator {

    Object evaluate(Object value, Map<String, Object> context) {
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()), evaluate(entry.getValue(), context));
            }
            return resolved;
        }
        if (value instanceof List<?> listValue) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : listValue) {
                resolved.add(evaluate(item, context));
            }
            return resolved;
        }
        if (!(value instanceof String stringValue)) {
            return value;
        }
        String trimmed = stringValue.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return evaluateExpression(trimmed.substring(2, trimmed.length() - 1), context);
        }
        if (!stringValue.contains("${")) {
            return stringValue;
        }
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < stringValue.length()) {
            int start = stringValue.indexOf("${", cursor);
            if (start < 0) {
                builder.append(stringValue.substring(cursor));
                break;
            }
            builder.append(stringValue, cursor, start);
            int end = stringValue.indexOf('}', start);
            Object resolved = evaluateExpression(stringValue.substring(start + 2, end), context);
            builder.append(resolved == null ? "" : resolved);
            cursor = end + 1;
        }
        return builder.toString();
    }

    Object evaluateExpression(String expression, Map<String, Object> context) {
        return new Parser(expression, context).parse();
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Map<String, Object> context;
        private int position;

        private Parser(String expression, Map<String, Object> context) {
            this.tokens = new Tokenizer(expression).tokenize();
            this.context = context;
        }

        private Object parse() {
            Object value = parseOr();
            expect(TokenType.EOF);
            return value;
        }

        private Object parseOr() {
            Object left = parseAnd();
            while (match(TokenType.OR) || matchIdentifier("or")) {
                Object right = parseAnd();
                left = asBoolean(left) || asBoolean(right);
            }
            return left;
        }

        private Object parseAnd() {
            Object left = parseEquality();
            while (match(TokenType.AND) || matchIdentifier("and")) {
                Object right = parseEquality();
                left = asBoolean(left) && asBoolean(right);
            }
            return left;
        }

        private Object parseEquality() {
            Object left = parseComparison();
            while (true) {
                if (match(TokenType.EQ)) {
                    left = isEqual(left, parseComparison());
                } else if (match(TokenType.NE)) {
                    left = !isEqual(left, parseComparison());
                } else {
                    return left;
                }
            }
        }

        private Object parseComparison() {
            Object left = parseAdditive();
            while (true) {
                if (match(TokenType.GT)) {
                    left = compare(left, parseAdditive()) > 0;
                } else if (match(TokenType.GE)) {
                    left = compare(left, parseAdditive()) >= 0;
                } else if (match(TokenType.LT)) {
                    left = compare(left, parseAdditive()) < 0;
                } else if (match(TokenType.LE)) {
                    left = compare(left, parseAdditive()) <= 0;
                } else {
                    return left;
                }
            }
        }

        private Object parseAdditive() {
            Object left = parseMultiplicative();
            while (true) {
                if (match(TokenType.PLUS)) {
                    left = plus(left, parseMultiplicative());
                } else if (match(TokenType.MINUS)) {
                    left = normalizeNumber(numeric(left).subtract(numeric(parseMultiplicative())));
                } else {
                    return left;
                }
            }
        }

        private Object parseMultiplicative() {
            Object left = parseUnary();
            while (true) {
                if (match(TokenType.STAR)) {
                    left = normalizeNumber(numeric(left).multiply(numeric(parseUnary())));
                } else if (match(TokenType.SLASH)) {
                    BigDecimal right = numeric(parseUnary());
                    if (BigDecimal.ZERO.compareTo(right) == 0) {
                        throw new IllegalStateException("division by zero");
                    }
                    left = normalizeNumber(numeric(left).divide(right, 10, java.math.RoundingMode.HALF_UP).stripTrailingZeros());
                } else {
                    return left;
                }
            }
        }

        private Object parseUnary() {
            if (match(TokenType.NOT) || matchIdentifier("not")) {
                return !asBoolean(parseUnary());
            }
            if (match(TokenType.MINUS)) {
                return normalizeNumber(numeric(parseUnary()).negate());
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            Token token = peek();
            if (match(TokenType.NUMBER)) {
                return parseNumber(token.text());
            }
            if (match(TokenType.STRING)) {
                return token.text();
            }
            if (match(TokenType.LPAREN)) {
                Object value = parseOr();
                expect(TokenType.RPAREN);
                return value;
            }
            if (match(TokenType.IDENTIFIER)) {
                return resolveIdentifier(token.text());
            }
            throw new IllegalStateException("unsupported expression near token: " + token.text());
        }

        private Object resolveIdentifier(String firstSegment) {
            if ("true".equalsIgnoreCase(firstSegment)) {
                return true;
            }
            if ("false".equalsIgnoreCase(firstSegment)) {
                return false;
            }
            if ("null".equalsIgnoreCase(firstSegment) || "none".equalsIgnoreCase(firstSegment)) {
                return null;
            }
            List<String> segments = new ArrayList<>();
            segments.add(firstSegment);
            while (match(TokenType.DOT)) {
                Token next = expect(TokenType.IDENTIFIER);
                segments.add(next.text());
            }
            Object current = context.get(segments.get(0));
            for (int index = 1; index < segments.size(); index++) {
                if (!(current instanceof Map<?, ?> map)) {
                    return null;
                }
                current = map.get(segments.get(index));
            }
            return current;
        }

        private boolean matchIdentifier(String keyword) {
            if (peek().type() == TokenType.IDENTIFIER && keyword.equalsIgnoreCase(peek().text())) {
                position++;
                return true;
            }
            return false;
        }

        private boolean match(TokenType tokenType) {
            if (peek().type() == tokenType) {
                position++;
                return true;
            }
            return false;
        }

        private Token expect(TokenType tokenType) {
            Token token = peek();
            if (token.type() != tokenType) {
                throw new IllegalStateException("expected token " + tokenType + " but got " + token.text());
            }
            position++;
            return token;
        }

        private Token peek() {
            return tokens.get(position);
        }

        private static Object plus(Object left, Object right) {
            if (left instanceof String || right instanceof String) {
                return String.valueOf(left) + right;
            }
            return normalizeNumber(numeric(left).add(numeric(right)));
        }

        private static int compare(Object left, Object right) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1;
            }
            if (right == null) {
                return 1;
            }
            if (left instanceof Number || right instanceof Number) {
                return numeric(left).compareTo(numeric(right));
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        }

        private static boolean isEqual(Object left, Object right) {
            if (left instanceof Number || right instanceof Number) {
                return compare(left, right) == 0;
            }
            return Objects.equals(left, right);
        }

        private static boolean asBoolean(Object value) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number numberValue) {
                return numberValue.doubleValue() != 0D;
            }
            if (value == null) {
                return false;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }

        private static BigDecimal numeric(Object value) {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return new BigDecimal(stringValue.trim());
            }
            throw new IllegalStateException("numeric operand required but got " + value);
        }

        private static Object normalizeNumber(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            if (stripped.scale() <= 0) {
                try {
                    return stripped.intValueExact();
                } catch (ArithmeticException ignored) {
                    return stripped.longValue();
                }
            }
            return stripped.doubleValue();
        }

        private static Object parseNumber(String text) {
            BigDecimal value = new BigDecimal(text);
            return normalizeNumber(value);
        }
    }

    private static final class Tokenizer {
        private final String expression;
        private int cursor;

        private Tokenizer(String expression) {
            this.expression = expression;
        }

        private List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (cursor < expression.length()) {
                char current = expression.charAt(cursor);
                if (Character.isWhitespace(current)) {
                    cursor++;
                    continue;
                }
                if (Character.isDigit(current)) {
                    tokens.add(readNumber());
                    continue;
                }
                if (current == '\'' || current == '"') {
                    tokens.add(readString(current));
                    continue;
                }
                if (Character.isLetter(current) || current == '_') {
                    tokens.add(readIdentifier());
                    continue;
                }
                if (cursor + 1 < expression.length()) {
                    String pair = expression.substring(cursor, cursor + 2);
                    switch (pair) {
                        case "&&" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.AND, pair));
                            continue;
                        }
                        case "||" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.OR, pair));
                            continue;
                        }
                        case "==" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.EQ, pair));
                            continue;
                        }
                        case "!=" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.NE, pair));
                            continue;
                        }
                        case ">=" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.GE, pair));
                            continue;
                        }
                        case "<=" -> {
                            cursor += 2;
                            tokens.add(new Token(TokenType.LE, pair));
                            continue;
                        }
                        default -> {
                        }
                    }
                }
                cursor++;
                tokens.add(switch (current) {
                    case '+' -> new Token(TokenType.PLUS, "+");
                    case '-' -> new Token(TokenType.MINUS, "-");
                    case '*' -> new Token(TokenType.STAR, "*");
                    case '/' -> new Token(TokenType.SLASH, "/");
                    case '(' -> new Token(TokenType.LPAREN, "(");
                    case ')' -> new Token(TokenType.RPAREN, ")");
                    case '.' -> new Token(TokenType.DOT, ".");
                    case '!' -> new Token(TokenType.NOT, "!");
                    case '>' -> new Token(TokenType.GT, ">");
                    case '<' -> new Token(TokenType.LT, "<");
                    default -> throw new IllegalStateException("unsupported token in expression: " + current);
                });
            }
            tokens.add(new Token(TokenType.EOF, "<eof>"));
            return tokens;
        }

        private Token readNumber() {
            int start = cursor;
            cursor++;
            while (cursor < expression.length()) {
                char current = expression.charAt(cursor);
                if (!Character.isDigit(current) && current != '.') {
                    break;
                }
                cursor++;
            }
            return new Token(TokenType.NUMBER, expression.substring(start, cursor));
        }

        private Token readString(char quote) {
            cursor++;
            StringBuilder builder = new StringBuilder();
            while (cursor < expression.length()) {
                char current = expression.charAt(cursor++);
                if (current == '\\' && cursor < expression.length()) {
                    char escaped = expression.charAt(cursor++);
                    builder.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '\'' -> '\'';
                        case '"' -> '"';
                        default -> escaped;
                    });
                    continue;
                }
                if (current == quote) {
                    return new Token(TokenType.STRING, builder.toString());
                }
                builder.append(current);
            }
            throw new IllegalStateException("unterminated string literal");
        }

        private Token readIdentifier() {
            int start = cursor;
            cursor++;
            while (cursor < expression.length()) {
                char current = expression.charAt(cursor);
                if (!Character.isLetterOrDigit(current) && current != '_') {
                    break;
                }
                cursor++;
            }
            return new Token(TokenType.IDENTIFIER, expression.substring(start, cursor));
        }
    }

    private record Token(TokenType type, String text) {
    }

    private enum TokenType {
        IDENTIFIER,
        NUMBER,
        STRING,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        LPAREN,
        RPAREN,
        DOT,
        NOT,
        AND,
        OR,
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        EOF
    }
}
