package org.openhab.binding.somfytahoma.internal;

/**
 * Created by Ondřej Pečta on 10. 8. 2016.
 */
public class SomfyTahomaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SomfyTahomaException(String message) {
        super(message);
    }

    public SomfyTahomaException(final Throwable cause) {
        super(cause);
    }

    public SomfyTahomaException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
