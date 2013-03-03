Deja vu
=======

h2.Introduction
Deja vu is a tracing framework that allows you to create sandboxed runnable traces replicating traces from production environments.
It works by annotating the code you want traced and uses aspectj for weaving in tracing behaviour.

Traces consists of runnable code (a unit test) and such tests can also manually be constructed which means Deja vu can be seen as a test framework
as well.


h2. Run/Re-run
The idea is that once a traced method has been run it is possible, by using the framework, to re-run it with the exact same execution path as the
original run.

The methods you want traced can done so by annotating them @Traced, example:

<code>
@Traced
public void findUserById( String id ) {
   ///...
}
<code>

The framework must be provided an implementation of the interface TraceCallback. This interface has a method that will be called
upon every completed call to @Traced methods.

h2. Pure/Impure
The goal of the framework is to produce a deterministic sandboxed runnable trace having the same execution path as the original.
For this to be possible the framework must know explicitly what methods have behaviour making it either environment dependent (e.g.
reading from a database) or non-deterministic (e.g. making an execution path decision based on a random number).

Pure code is defined as code without: randomization, user inputs, global time dependencies, dependencies to external systems
(integration points), or access to shared mutable state. (Note that pure does not refer to the functional definition, because
Deja vu pure code is allowed to have side effects).

For tracing to work all code that does not pass the "pure" definition must be annotated @Impure:

<code>
@Impure
public String randomUUID() {
    return UUID.randomUUID().toString();
}
</code>


h2. Marshaling

Since Deja vu is about running code a Trace instance can be marshaled using the class Marshaller. A trace is marshaled to
the source code of a unit test.



h2. @AttachThread

It is possible to get traces even when multi threading is involved. However this requires the framework to understand when
parts of the trace is executed in another thread.

<code>
@AttachThread
public void run( Runnable runnable ) {
    new Thread( runnable ).start();
}
</code>

Using @AttachThread the framework will assume that all instances of runnable passed as arguments is about to be run
in a separate thread and the trace will only be completed when all such runnables are also finished running.

h2. Threaded re-run

When re-running a threaded trace the pure parts of the different threads will run parallel - so we are not guaranteed
the exact same ordering of instruction execution among threads (but this should not matter as this code is "pure").
What is guaranteed, however, is the order of execution of @Impure parts (the framework will simply let threads wait if
they tend to pass @Impure points before their time).

h2. Setup

Since the framework is using aspectj there is an option of compile time weaving or runtime weaving. A typical setup
would be to have compile time weaving for production setup and compile time weaving for test setup.

h3. Compile time weaving

Using maven the following must be added to your pom.xml:
<pre>
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>aspectj-maven-plugin</artifactId>
                <version>1.4</version>
                <configuration>
                    <source>1.6</source>
                    <target>1.6</target>
                    <encoding>utf-8</encoding>
                    <complianceLevel>1.6</complianceLevel>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>test-compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</pre>

Any classes using the Deja vu annotation must be specified in the aop.xml file (standard aspectj setup).

h3. Runtime weaving

Runtime weaving can be done by adding a javaagent argument to the VM options for the compiler:
-javaagent:PATH TO ASPECTJWEAVER.jar

