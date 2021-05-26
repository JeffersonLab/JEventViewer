#############################3
# JEventViewer 2.0
#############################3

This software package creates a graphical user interface for looking at
EVIO format files event-by-event, although it can also look at any file
as a list of 32 bit integer (words). This version is compatible with
evio version 6 format. To run it, with Java 8 or later, simply execute:

    java org.jlab.coda.eventViewer.EventTreeFrame

Make sure that the jar file, JEventViewer-2.0.jar, and all the other jar files
in the java/jars directory, are in your CLASSPATH.

The alternative to that is executing the provided script:

    scripts/jeviodump

Note that the script is for CODA users and sets the classpath to:

    $CODA/common/jar

In other words, make sure that your environmental variable CODA is defined
and all the jar files are in that directory.


----- Installation -----

The code can be downloaded from its github site:

    git clone https://github.com/JeffersonLab/JEventViewer.git

The default branch is "2.0" but one can insure that by calling:

    git checkout 2.0

There’s the jar file JEventViewer-2.0.jar in the java/jars/java8 directory,
already pre-built with Java 8, so one does not need to build it.
There's another one in java/jars/java15 directory built with Java 15.

To build it simply do:
	ant jar
Other options can be seen by calling:
	ant help



    The output of "ant help" is:

    help:
        [echo] Usage: ant [ant options] <target1> [target2 | target3 | ...]
       
        [echo]        targets:
        [echo]        help       - print out usage
        [echo]        env        - print out build file variables' values
        [echo]        compile    - compile java files
        [echo]        clean      - remove class files
        [echo]        cleanall   - remove all generated files
        [echo]        jar        - compile and create jar file
        [echo]        install    - create jar file and install into 'prefix'
        [echo]                     if given on command line by -Dprefix=dir',
        [echo]                     else install into CODA if defined
        [echo]        uninstall  - remove jar file previously installed into 'prefix'
        [echo]                     if given on command line by -Dprefix=dir',
        [echo]                     else installed into CODA if defined
        [echo]        all        - clean, compile and create jar file
        [echo]        javadoc    - create javadoc documentation
        [echo]        developdoc - create javadoc documentation for developer
        [echo]        undoc      - remove all javadoc documentation
        [echo]        prepare    - create necessary directories


    Although this is fairly self-explanatory, executing ant is the same as ant compile.
    That will compile all the java. All compiled code is placed in the generated ./build directory.
    If the user wants a jar file, execute ant jar to place the resulting file in the ./build/lib directory.
    The java command in the user’s path will be the one used to do the compilation.


----- Documentation -----

You can read the user documentation in either a pdf or word doc.
In the repository, it’s located in the doc/users_guide directory.
There is javadoc that can be generated (ant javadoc or ant developdoc).

 
----- Prerequisites -----

The other jar files necessary to compile JEventViewer-2.0.jar are in the java/jars directory.
They are compiled with Java 8. In addition, there are 2 subdirectories:

    1) java/jars/java8, which contains all such jars compiled with Java 8, and
    2) java/jars/java15 which contains all jars compiled with Java 15.

If a jar file is not available in Java 15 use the Java 8 version.


To generate these jar files, go to their respective github sites and follow the directions there:

    https://github.com/JeffersonLab/disruptor
    https://github.com/lz4/lz4-java
    https://github.com/JeffersonLab/evio
    https://github.com/JeffersonLab/et
    https://github.com/JeffersonLab/cMsg
