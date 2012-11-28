package pl.touk.snap;

import grails.test.mixin.support.MixinMethod;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.grails.compiler.injection.GrailsArtefactClassInjector;
import org.codehaus.groovy.grails.compiler.injection.test.MockTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.junit.BeforeClass;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;


/**
 * Used by the {@link SharedApplicationMock} local transformation to add
 * mocking capabilities for the given classes.
 *
 * @author Tomasz Kalkosiński
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class SharedApplicationMockTransformation extends MockTransformation {

    private static final ClassNode MY_TYPE = new ClassNode(SharedApplicationMock.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    public static final String SET_UP_CLASS_METHOD = "setUpClass";
    public static final ClassNode BEFORE_CLASS_CLASS_NODE = new ClassNode(BeforeClass.class);
    public static final AnnotationNode BEFORE_CLASS_ANNOTATION = new AnnotationNode(BEFORE_CLASS_CLASS_NODE);

    @Override
    public void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: " + astNodes[0].getClass() +
                    " / " + astNodes[1].getClass());
        }

        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];
        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) parent;
        String cName = classNode.getName();
        if (classNode.isInterface()) {
            error(source, "Error processing interface '" + cName + "'. " + MY_TYPE_NAME +
                    " not allowed for interfaces.");
        }

        ListExpression values = getListOfClasses(node);
        if (values == null) {
            error(source, "Error processing class '" + cName + "'. " + MY_TYPE_NAME +
                    " annotation expects a class or a list of classes to mock");
            return;
        }

        if (isJunit3Test(classNode)) {
            error(source, "Error processing class '" + cName + "'. " + MY_TYPE_NAME +
                    " annotation is not supported to use with JUnit 3. Please upgrade to JUnit 4.");
        }

        weaveMixinClass(classNode, SharedApplicationUnitTestMixin.class);
        weaveMockCollaboratorToSetupSpec(classNode, values.getExpressions());
    }

    protected void weaveMockCollaboratorToSetupSpec(ClassNode classNode, List<Expression> targetClasses) {
        BlockStatement junitBeforeClassBlockStatement = getJunit4BeforeClassBlockStatement(classNode);
        addAddMockDomains(junitBeforeClassBlockStatement, targetClasses);
    }

    protected BlockStatement getJunit4BeforeClassBlockStatement(ClassNode classNode) {
        Map<String, MethodNode> declaredMethodsMap = classNode.getDeclaredMethodsMap();
        for (MethodNode methodNode : declaredMethodsMap.values()) {
            if (isDeclaredBeforeClassMethod(methodNode)) {
                Statement code = getMethodBody(methodNode);
                return (BlockStatement) code;
            }
        }
        return getJunit4SetupClass(classNode);
    }

    private Statement getMethodBody(MethodNode methodNode) {
        Statement code = methodNode.getCode();
        if (!(code instanceof BlockStatement)) {
            BlockStatement body = new BlockStatement();
            body.addStatement(code);
            code = body;
        }
        return code;
    }

    private boolean isDeclaredBeforeClassMethod(MethodNode methodNode) {
        return isPublicInstanceMethod(methodNode) && hasAnnotation(methodNode, BeforeClass.class) && !hasAnnotation(methodNode, MixinMethod.class);
    }

    private boolean isPublicInstanceMethod(MethodNode methodNode) {
        return !methodNode.isSynthetic() && !methodNode.isStatic() && methodNode.isPublic();
    }

    private BlockStatement getJunit4SetupClass(ClassNode classNode) {
        MethodNode setupMethod = classNode.getMethod(SET_UP_CLASS_METHOD, GrailsArtefactClassInjector.ZERO_PARAMETERS);
        if (setupMethod == null) {
            setupMethod = new MethodNode(SET_UP_CLASS_METHOD, Modifier.PUBLIC | Modifier.STATIC, ClassHelper.VOID_TYPE,GrailsArtefactClassInjector.ZERO_PARAMETERS,null,new BlockStatement());
            setupMethod.addAnnotation(MIXIN_METHOD_ANNOTATION);
            classNode.addMethod(setupMethod);
        }
        if (setupMethod.getAnnotations(BEFORE_CLASS_CLASS_NODE).size() == 0) {
            setupMethod.addAnnotation(BEFORE_CLASS_ANNOTATION);
        }
        return getOrCreateMethodBody(classNode, setupMethod, SET_UP_CLASS_METHOD);

    }

    protected void addAddMockDomains(BlockStatement methodBody, List<Expression> targetClasses) {
        // Ensure that initializeDatastoreImplementation is called first
        methodBody.getStatements().add(new ExpressionStatement(new StaticMethodCallExpression(MY_TYPE, "initializeDatastoreImplementation", new ArgumentListExpression())));

        for(Expression expression : targetClasses)
            if (expression instanceof ClassExpression) {
                addMockDomain(methodBody, (ClassExpression) expression);
        }
    }

    protected void addMockDomain(BlockStatement methodBody, ClassExpression classExpression) {
        ArgumentListExpression args = new ArgumentListExpression();
        args.addExpression(classExpression);
        // Add after initializeDatastoreImplementation call
        methodBody.getStatements().add(new ExpressionStatement(new MethodCallExpression(THIS_EXPRESSION, "staticMockDomain", args)));
    }
}
