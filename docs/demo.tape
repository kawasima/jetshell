# JetShell Demo — Resolve Spring Boot and run Hello World

Output demo.gif

Require curl

Set Shell bash
Set FontSize 16
Set Width 1100
Set Height 650
Set Theme "Catppuccin Mocha"
Set Padding 20
Set TypingSpeed 30ms
Set WaitTimeout 90s
Set WaitPattern /->/

# 1. Download JetShell from GitHub Releases
Sleep 500ms
Type "curl -fsSL https://github.com/kawasima/jetshell/releases/download/v1.0.1/jetshell -o jetshell && chmod +x jetshell"
Sleep 200ms
Enter
Sleep 5s

# 2. Launch JetShell
Type "./jetshell"
Enter
Wait+Screen /->/
Sleep 600ms

# 3. Resolve spring-boot-starter — show tab completion on artifact coordinate
Type "/resolve org.springframework.boot:spring-boot-starter:"
Sleep 500ms
Tab
Sleep 1500ms
Type "3.4.3"
Enter
Wait+Screen@90s /->/
Sleep 1000ms

# 4. Import packages
Type "import org.springframework.context.annotation.*;"
Enter
Wait+Screen /->/
Sleep 200ms

Type "import org.springframework.stereotype.*;"
Enter
Wait+Screen /->/
Sleep 200ms

# 5. Define Greeter @Component
Type "@Component class Greeter {"
Enter
Sleep 150ms
Type "    public String greet() {"
Enter
Sleep 150ms
Type `        return "Hello, World from JetShell!";`
Enter
Sleep 150ms
Type "    }"
Enter
Sleep 150ms
Type "}"
Enter
Wait+Screen /->/
Sleep 500ms

# 6. Boot ApplicationContext and invoke the Bean
Type "var ctx = new AnnotationConfigApplicationContext(Greeter.class);"
Enter
Wait+Screen /->/
Sleep 500ms

Type "System.out.println(ctx.getBean(Greeter.class).greet());"
Enter
Wait+Screen /->/
Sleep 1500ms

# 7. Exit
Type "/exit"
Enter
Sleep 1s
