grails-snap-plugin
==================

Experiments in Grails unit and integration testing.

@SharedApplicationMock
----------------------

@SharedApplicationMock annotation can be used for Grails unit tests instead of @Mock annotation. It changes test suite workflow:

* before test suite starts, it creates Grails application context only once and reuses it across test suite,
* before test suite starts, it mocks domain classes only once,
* after seach test it cleans saved domain instances

I suggest that you can use this annotation if you're aware of shared application context and you're sure it won't break anything. I find it useful with controller unit tests, where my application context does not change. To compare its efectiveness I use a complex unit test suite. It tests controller with 36 tests and 16 mocked domains. With @Mock annotation test execution time is about 14 seconds, while 9-10 with @SharedApplicationMock. Not that much, but it was worthy experiment to find out.

Annotation has a major drawback - it has to be annotated first, before @TestFor annotation. Otherwise it won't mix a mixin in compile time.

It can be used for Grails 2 projects.

Example:

```groovy
@SharedApplicationMock([Author, Book, Paragraph])
@TestFor(BookController)
class BookControllerSpec extends Specification {
  // ...
}
```

