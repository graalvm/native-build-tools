package org.graalvm.demo;

import org.apache.commons.math3.complex.Complex;

public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public Complex complex(double real, double imaginary) {
        return new Complex(real, imaginary);
    }

}
