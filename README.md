----------------------------

# **JEventViewer **

----------------------------

This software package creates a graphical user interface for looking at
EVIO format files event-by-event, although it can also look at any file
as a listing of 32-bit integers. This version is compatible with both
evio version 4 & 6 formats. To run it, with Java 8 or later, simply execute:

    java org.jlab.coda.eventViewer.EventTreeFrame

Make sure that the jar file, JEventViewer-2.x.jar, and all the other jar files
in the java/jars directory, are in your CLASSPATH.

The alternative to that is executing the provided script:

    scripts/jeviodump

Note that the script is for CODA users and sets the classpath to:

    $CODA/common/jar

In other words, make sure that your environmental variable CODA is defined
and all the jar files are in that directory.


### **Installation**

The code can be downloaded from its github site:

    git clone https://github.com/JeffersonLab/JEventViewer.git
    git checkout

There’s the jar file JEventViewer-2.x.jar in the java/jars/java8 directory,
already pre-built with Java 8, so one does not need to build it.
There's another one in java/jars/java15 directory built with Java 15.

To build a new jar file do:

	./gradlew
The newly created jar file will be places in `build/lib/`. To change java version

In addition to standard gradle options, the following commands can be run:

- `./gradlew env` prints variables and paths used for building and installation
- `./gradlew javadoc` create javadoc documentation 
- `./gradlew developdoc` create javadoc documentation for developers 
- `./gradlew undoc` remove all javadoc documentation 
- `./gradlew install -Pprefix=/your/install/path` create javadoc documentation for developers 
- `./gradlew uninstall` remove jar file previously installed into `prefix`, if given on command line by `-Dprefix=dir`. Else uninstall from `$CODA` if defined.

To see a full list of options, run `./gradlew tasks`. To change java versions, edit the line 

	toolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
 with desired java version in the file `build.gradle.kts`.


### **Documentation**

You can read the user documentation in either a pdf or word doc.
In the repository, it’s located in the `doc/users_guide` directory.
There is javadoc that can be generated (`./gradlew javadoc` or `./gradlew developdoc`)
but it's of little or no use to the user, more relevant to the developer.

Documentation on GitHub:

* [All Documentation](https://jeffersonlab.github.io/JEventViewer)

 
### **Prerequisites**

The other jar files necessary to compile JEventViewer-2.x.jar are in the java/jars directory.
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
