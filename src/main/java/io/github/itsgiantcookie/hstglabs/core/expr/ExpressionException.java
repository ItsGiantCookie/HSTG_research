package io.github.itsgiantcookie.hstglabs.core.expr;

/** A template expression could not be parsed or evaluated; {@link #position()} is the character offset. */
public final class ExpressionException extends RuntimeException {

    private final int position;

    public ExpressionException(String message, int position) {
        super(message + " (at " + position + ")");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
