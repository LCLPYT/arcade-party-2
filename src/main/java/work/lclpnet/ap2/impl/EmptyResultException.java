package work.lclpnet.ap2.impl;

import work.lclpnet.ap2.Prototype;

import java.io.Serial;
import java.util.NoSuchElementException;

@Prototype
public class EmptyResultException extends NoSuchElementException {

    @Serial
    private static final long serialVersionUID = -8300523463791870390L;

    public EmptyResultException() {
        super();
    }

    public EmptyResultException(String s, Throwable cause) {
        super(s, cause);
    }

    public EmptyResultException(Throwable cause) {
        super(cause);
    }

    public EmptyResultException(String s) {
        super(s);
    }
}
