# JetShell

[![Release](https://img.shields.io/github/v/release/kawasima/jetshell)](https://github.com/kawasima/jetshell/releases/latest)
[![License](https://img.shields.io/badge/license-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html)
[![Java](https://img.shields.io/badge/java-21%2B-orange.svg)](https://adoptium.net/)

**JetShell** stands for **J**Shell **E**xtension **T**ool.

Exploring an unfamiliar library in Java normally means creating a project, writing a `pom.xml`, waiting for an IDE to index — all before writing a single line of code.

JetShell removes that friction. Type a library name, press Tab to complete the Maven coordinates, and `/resolve` downloads it straight to your classpath. From there you can call APIs interactively, read Javadoc with `/doc`, and browse source code with `/source` — all without creating a project.

![JetShell Demo](docs/demo.gif)

## Requirements

- Java 21+

## Installation

### Download

Download the latest release from [GitHub Releases](https://github.com/kawasima/jetshell/releases).

The release artifact is a self-contained executable JAR (Really Executable JAR). Make it executable and run directly:

```shell
chmod +x jetshell
./jetshell
```

### Build from source

```shell
git clone https://github.com/kawasima/jetshell.git
cd jetshell
mvn package -DskipTests
./target/jetshell
```

## Usage

### Starting JetShell

```shell
$ jetshell

|  Welcome to JetShell -- Version 1.0.0
|  Type /help for help

->
```

### Resolving artifacts

Use `/resolve` to download a Maven artifact and add it to the classpath. Tab completion suggests candidates from both your local `~/.m2/repository` and Maven Central.

```
-> /resolve org.apache.commons:commons-lang3:3.14.0
|  Path /home/user/.m2/repository/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar added to classpath

-> import org.apache.commons.lang3.StringUtils;
-> StringUtils.capitalize("hello world")
|  Expression value is: "Hello world"
|    assigned to temporary variable $1 of type String
```

### Tab completion

Pressing `Tab` completes artifact coordinates incrementally:

```
-> /resolve org.apache.commons:<Tab>
commons-beanutils    commons-codec    commons-collections4    commons-compress
commons-csv          commons-io       commons-lang3           commons-math3
...

-> /resolve org.apache.commons:commons-lang3:<Tab>
3.12.0    3.13.0    3.14.0
```

Local repository results appear instantly; Maven Central results appear after a brief search (shown with a spinner).

### Viewing documentation

Use `/doc` to display Javadoc for any class, method, or field. Works with simple names after import:

```
-> /doc java.util.List
|  java.util.List<E>
|
|  An ordered collection, where the user has precise control over where in the
|  list each element is inserted. ...

-> import org.apache.commons.lang3.StringUtils;
-> /doc StringUtils.isEmpty(
|  boolean StringUtils.isEmpty(CharSequence cs)
|
|  Checks if a CharSequence is empty ("") or null. ...
```

Source JARs are downloaded automatically if needed to retrieve Javadoc for third-party libraries.

### Viewing source code

Use `/source` to display the source code of a resolved class. Simple names work after import:

```
-> /source org.apache.commons.lang3.StringUtils
|   1: /*
|   2:  * Licensed to the Apache Software Foundation (ASF) ...
...

-> import org.apache.commons.lang3.RandomStringUtils;
-> /source RandomStringUtils
|   1: public class RandomStringUtils {
...
```

Source JARs are downloaded automatically if not already present locally.

### Viewing dependencies

Use `/deps` to inspect what has been resolved in the current session:

```
-> /deps
|  org.apache.commons:commons-lang3:3.14.0 (1 files)
|  com.google.guava:guava:33.2.1-jre (9 files)

-> /deps com.google.guava:guava:33.2.1-jre
|  com.google.guava:guava:jar:33.2.1-jre
|    com.google.guava:failureaccess:jar:1.0.2
|    com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava
|    com.google.guava:guava-parent:pom:33.2.1-jre
|    ...
```

### Command reference

| Command | Description |
| ------- | ----------- |
| `/resolve <spec>` | Resolve a Maven artifact and add it to the classpath |
| `/deps [spec]` | List resolved artifacts, or show dependency tree for a spec |
| `/doc <expression>` | Show Javadoc for a class, method, or field |
| `/source <class>` | Display source code of a class |
| `/list [all\|start\|<id>]` | List evaluated snippets |
| `/vars` | List declared variables and their values |
| `/methods` | List declared methods |
| `/classes` | List declared classes |
| `/imports` | List active imports |
| `/classpath <path>` | Add a path to the classpath manually |
| `/open <file>` | Load and evaluate a `.jsh` script file |
| `/save [all\|history\|start] <file>` | Save snippets to a file |
| `/reset` | Reset the JShell state |
| `/reload [restore\|quiet]` | Reset and replay history |
| `/exit` | Exit JetShell |

### Artifact coordinate format

```
<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
```

Examples:

- `org.apache.commons:commons-lang3:3.14.0`
- `com.google.guava:guava:33.2.1-jre`
- `org.springframework.boot:spring-boot-starter-web:3.4.3`

### Session history

Command history is persisted across sessions in `~/.jetshell_history`. Use the up/down arrow keys to navigate previous inputs.

### JVM options

Pass JVM options via the `JAVA_OPTS` environment variable:

```shell
JAVA_OPTS="-Xmx1g" jetshell
```

## License

GNU General Public License v2.0
