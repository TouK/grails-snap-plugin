package pl.touk.snap;


import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to define a mocked collaborator when using the {@link pl.touk.snap.SharedApplicationUnitTestMixin} mixin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@GroovyASTTransformationClass("pl.touk.snap.SharedApplicationMockTransformation")
public @interface SharedApplicationMock {
    Class<?>[] value();
}