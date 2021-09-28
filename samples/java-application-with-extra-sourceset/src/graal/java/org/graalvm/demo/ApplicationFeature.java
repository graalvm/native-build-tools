package org.graalvm.demo;

import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@AutomaticFeature
public class ApplicationFeature implements Feature {
    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        try (PrintWriter wrt = new PrintWriter(new FileWriter(new File(access.getImagePath().toFile().getParentFile(), "app.txt")))) {
            wrt.println("-------------------\n");
            wrt.println("Application Feature\n");
            wrt.println("-------------------\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
