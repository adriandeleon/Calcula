package com.calcula.plot;

/** Something that cannot be plotted, with a reason worth showing the user. */
public class PlotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlotException(String message) {
        super(message);
    }
}
