# Apache Maven

Apache Maven is a software project management and comprehension tool. Based on
the Project Object Model (POM), Maven manages a project's build, dependencies,
reporting, and documentation from a central configuration file.

This repository contains the source for Apache Maven 3.2.2-SNAPSHOT.

## Project resources

- [Apache Maven website](https://maven.apache.org/)
- [Maven documentation](https://maven.apache.org/guides/)
- [Issue tracker](https://issues.apache.org/jira/browse/MNG)
- [Developer documentation](https://maven.apache.org/guides/development/guide-building-maven.html)

## Building from source

The bootstrap build requires:

- JDK 6 or later
- Apache Ant 1.8 or later

Choose an empty installation directory outside the repository and assign it to
`M2_HOME`:

```sh
export M2_HOME="$HOME/apps/maven/apache-maven-3.2.2-SNAPSHOT"
ant
```

The Ant build downloads the required dependencies, builds the Maven modules,
creates a binary distribution, and extracts it into `M2_HOME`. If that directory
already exists, the build asks before replacing it.

To use a separate local repository during the bootstrap build, run:

```sh
ant -Dmaven.repo.local=/path/to/local/repository
```

After the build completes, verify the new installation:

```sh
"$M2_HOME/bin/mvn" --version
```

## Testing

The default Ant bootstrap runs the Maven test suite. To run the tests with an
existing Maven installation, use:

```sh
mvn test
```

## License

Apache Maven is licensed under the [Apache License, Version 2.0](LICENSE).
