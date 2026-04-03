package cz.fel.cvut.beevidence_and_cyber.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
